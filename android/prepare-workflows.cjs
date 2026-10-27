const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const source=process.env.COMFYUI_WORKFLOWS||path.resolve(process.argv[2]||path.join(__dirname,'../workflows'));
const server=process.env.COMFYUI_URL||'http://127.0.0.1:8188';
async function prepare(){
 const response=await fetch(server+'/object_info');if(!response.ok)throw Error('Cannot read current ComfyUI schema');const info=await response.json();
 const plans={text:{file:'手机文生图.json',root:50,positive:80,negative:37,size:10,seed:41,seedField:'noise_seed'},single:{file:'手机图改图 (单图）.json',root:29,positive:123,negative:85,size:82,seed:53,seedField:'seed'},dual:{file:'手机图改图 (双图）.json',root:29,positive:123,negative:85,size:82,seed:53,seedField:'seed'},anima:{file:'手机Anima文生图.json',root:46,positive:51,negative:12,size:28,seed:19,seedField:'seed'}};
 const all={};
 for(const [key,plan]of Object.entries(plans)){
  const graph=JSON.parse(fs.readFileSync(path.join(source,plan.file),'utf8')),nodes=new Map(graph.nodes.map(n=>[n.id,n])),links=new Map(graph.links.map(l=>[l[0],l])),api={};
  function visit(id){const n=nodes.get(id);if(!n||n.mode===4||n.mode===2)return false;if(api[id])return true;assert(info[n.type],`Unknown node ${n.type}`);const item={class_type:n.type,inputs:{},_meta:{title:n.title||n.type}};api[id]=item;let index=0;
   for(const input of n.inputs||[]){if(input.widget){if(['upload','preview'].includes(input.name))continue;const value=n.widgets_values?.[index++];if(value!==undefined&&input.link==null)item.inputs[input.name]=value;if(['seed','noise_seed'].includes(input.name)&&['randomize','fixed','increment','decrement'].includes(n.widgets_values?.[index]))index++;}if(input.link!=null){const link=links.get(input.link);assert(link,`Missing link ${input.link}`);if(visit(link[1]))item.inputs[input.name]=[String(link[1]),link[2]];}}
   return true;
  }
  visit(plan.root);
  assert.equal(api[plan.positive].class_type,'ExcelPromptGlossary','Positive prompt must enter the actual translation chain');
  api[plan.positive].inputs.text='';const negativeField=['single','dual'].includes(key)?'prompt':'text';api[plan.negative].inputs[negativeField]='';api[plan.seed].inputs[plan.seedField]=0;api[plan.root].inputs.filename_prefix='ComfyMobile/'+key;
  if(['single','dual'].includes(key)){
   assert(api['118']&&api['118'].class_type==='GetImageSizeAndCount');api['72'].inputs.image='mobile-placeholder.png';if(key==='dual')api['90'].inputs.image='mobile-placeholder-2.png';
   for(const [axis,slot]of [['width',1],['height',2]]){const id='mobile_'+axis;api[id]={class_type:'ComfyMathExpression',inputs:{expression:'max(64, round(a * b / 8) * 8)','values.a':['118',slot],'values.b':1},_meta:{title:'原图'+axis+'按比例缩放'}};api[plan.size].inputs[axis]=[id,1];}
  }else{api[plan.size].inputs.width=1024;api[plan.size].inputs.height=1024;}
  const mapping={prompt:api,output:String(plan.root),positive:String(plan.positive),negative:String(plan.negative),size:String(plan.size),seed:String(plan.seed),seedField:plan.seedField,textField:'text',negativeField};
  if(key==='anima'){
   mapping.modelNode='44';mapping.samplerNode='19';mapping.artistNode='58';mapping.defaultArtist=api['58'].inputs.artist;mapping.artistOptions=info.AnimaArtistGallery.input.required.artist[0];mapping.models={base:{file:'anima-base-v1.0.safetensors',steps:32,cfg:4},turbo:{file:'anima-turbo-v1.1.safetensors',steps:10,cfg:1}};
   const available=info.UNETLoader.input.required.unet_name[0];for(const model of Object.values(mapping.models))assert(available.includes(model.file),'Anima model is not installed');api['19'].inputs.steps=32;api['19'].inputs.cfg=4;
  }
  for(const [id,item]of Object.entries(api)){const def=info[item.class_type];assert(def,`Missing schema ${item.class_type}`);for(const name of Object.keys(def.input.required||{}))assert(name in item.inputs||Object.keys(item.inputs).some(key=>key.startsWith(name+'.')),`${key} ${id} missing ${name}`);for(const [name,value]of Object.entries(item.inputs))if(Array.isArray(value)&&value.length===2&&typeof value[0]==='string'){assert(api[value[0]],`${key} dangling link ${id}.${name}`);const actual=info[api[value[0]].class_type].output[value[1]],expected=def.input.required?.[name]?.[0]||def.input.optional?.[name]?.[0];assert(!expected||actual===expected||actual==='*'||expected==='*',`${key} incompatible ${id}.${name}: ${actual}/${expected}`);}}
  all[key]=mapping;console.log(`${key}: ${Object.keys(api).length} API nodes; translated positive ${plan.positive}.text; negative ${plan.negative}.${negativeField}`);
 }
 fs.writeFileSync(path.join(__dirname,'assets/workflows.json'),JSON.stringify(all),'utf8');
}
prepare().catch(error=>{console.error(error);process.exitCode=1});
