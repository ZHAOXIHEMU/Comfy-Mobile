param([string]$SdkRoot=$env:ANDROID_SDK_ROOT,[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$OutputEncoding=[Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$projectRoot=$PSScriptRoot
if(!$SdkRoot){$SdkRoot=Join-Path $projectRoot '../android-toolchain'}
$toolsRoot=Join-Path $SdkRoot 'build-tools/android-16'
if(!(Test-Path -LiteralPath (Join-Path $toolsRoot 'aapt2.exe'))){$toolsRoot=(Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe')} | Sort-Object {try{[version]$_.Name}catch{[version]"0.0"}} -Descending | Select-Object -First 1).FullName}
if(!$toolsRoot){throw 'Android build-tools not found. Set ANDROID_SDK_ROOT or -SdkRoot.'}
$androidJar=Join-Path $SdkRoot 'platforms/android-36/android.jar'
if(!(Test-Path -LiteralPath $androidJar)){$androidJar=Join-Path $SdkRoot 'platform/android-36/android.jar'}
if(!(Test-Path -LiteralPath $androidJar)){throw 'Install Android SDK Platform 36.'}
if($JavaHome){$javaRoot=Join-Path $JavaHome 'bin'}else{
 $javac=Get-Command javac.exe -ErrorAction SilentlyContinue
 if($javac){$javaRoot=Split-Path $javac.Source}
 if(!$javaRoot -or !(Test-Path -LiteralPath (Join-Path $javaRoot 'jar.exe'))){
  $settings=& java.exe -XshowSettings:properties -version 2>&1 | Out-String
  $match=[regex]::Match($settings,'java.home\s*=\s*([^\r\n]+)')
  if($match.Success){$javaRoot=Join-Path $match.Groups[1].Value.Trim() 'bin'}
 }
}
if(!$javaRoot -or !(Test-Path -LiteralPath (Join-Path $javaRoot 'jar.exe'))){throw 'Full JDK not found. Set JAVA_HOME or -JavaHome.'}
[xml]$manifest=Get-Content -LiteralPath (Join-Path $projectRoot 'AndroidManifest.xml') -Raw -Encoding UTF8
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$apkOutput='build/Comfy-Mobile-'+$version+'.apk' 
Push-Location -LiteralPath $projectRoot
try {
New-Item -ItemType Directory -Path 'build/classes','build/dex','build/gen' -Force | Out-Null
function Invoke-Checked($exe,$arguments){& $exe @arguments;if($LASTEXITCODE -ne 0){throw "Build failed: $exe ($LASTEXITCODE)"}}
Invoke-Checked (Join-Path $toolsRoot 'aapt2.exe') @('compile','--dir','res','-o','build/resources.zip')
Invoke-Checked (Join-Path $toolsRoot 'aapt2.exe') @('link','-I',$androidJar,'--manifest','AndroidManifest.xml','--java','build/gen','-A','assets','-o','build/unsigned.apk','build/resources.zip','--min-sdk-version','33','--target-sdk-version','36')
$sources=@(Get-ChildItem -LiteralPath 'src' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
$classpath=$androidJar+';'+(Join-Path $projectRoot 'lib/okhttp.jar')+';'+(Join-Path $projectRoot 'lib/okio.jar')
Invoke-Checked (Join-Path $javaRoot 'javac.exe') (@('-J-Duser.language=en','-J-Dfile.encoding=UTF-8','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',($androidJar+';'+(Join-Path $toolsRoot 'core-lambda-stubs.jar')),'-classpath',$classpath,'-d','build/classes')+$sources)
$classes=@(Get-ChildItem -LiteralPath 'build/classes' -Recurse -Filter '*.class' | Select-Object -ExpandProperty FullName)
Invoke-Checked (Join-Path $javaRoot 'java.exe') (@('-cp',(Join-Path $toolsRoot 'lib/d8.jar'),'com.android.tools.r8.D8','--min-api','33','--lib',$androidJar,'--output','build/dex','lib/okhttp.jar','lib/okio.jar')+$classes)
Invoke-Checked (Join-Path $javaRoot 'jar.exe') @('--update','--file','build/unsigned.apk','-C','build/dex','classes.dex')
Invoke-Checked (Join-Path $toolsRoot 'zipalign.exe') @('-f','-p','4','build/unsigned.apk','build/aligned.apk')
$signPath=Join-Path $projectRoot 'signing.json'
if(!(Test-Path -LiteralPath $signPath)){
 $bytes=New-Object byte[] 32;[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
 $sign=@{password=[Convert]::ToBase64String($bytes);alias='comfymobile'}
 [IO.File]::WriteAllText($signPath,($sign|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}else{$sign=Get-Content -LiteralPath $signPath -Raw | ConvertFrom-Json}
$env:COMFY_STORE_PASS=$sign.password
try{
 if(!(Test-Path -LiteralPath 'release.keystore')){Invoke-Checked (Join-Path $javaRoot 'keytool.exe') @('-genkeypair','-keystore','release.keystore','-alias',$sign.alias,'-storepass:env','COMFY_STORE_PASS','-keypass:env','COMFY_STORE_PASS','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Comfy Mobile, OU=Personal, O=Comfy Mobile, C=CN')}
 Invoke-Checked (Join-Path $javaRoot 'java.exe') @('-jar',(Join-Path $toolsRoot 'lib/apksigner.jar'),'sign','--ks','release.keystore','--ks-key-alias',$sign.alias,'--ks-pass','env:COMFY_STORE_PASS','--key-pass','env:COMFY_STORE_PASS','--out',$apkOutput,'build/aligned.apk')
 Invoke-Checked (Join-Path $javaRoot 'java.exe') @('-jar',(Join-Path $toolsRoot 'lib/apksigner.jar'),'verify','--verbose',$apkOutput)
}finally{Remove-Item Env:COMFY_STORE_PASS -ErrorAction SilentlyContinue}
Write-Output 'APK built and signature verified.'

}finally{Pop-Location}
