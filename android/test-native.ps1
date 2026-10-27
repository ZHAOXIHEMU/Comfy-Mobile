param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if($JavaHome){$bin=Join-Path $JavaHome 'bin'}else{$bin=Split-Path (Get-Command javac.exe).Source}
Push-Location -LiteralPath $PSScriptRoot
try {
 New-Item -ItemType Directory -Path build/native-tests -Force | Out-Null
 $files=@('BatchStore','BatchEngine','ArtistCache','AlbumIndex','ForegroundSession') | ForEach-Object {'src/com/comfymobile/app/'+$_+'.java'}
 $tests=@(Get-ChildItem test-native -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
 & (Join-Path $bin 'javac.exe') '-J-Duser.language=en' '-J-Dfile.encoding=UTF-8' -encoding UTF-8 -cp test-lib/json-tests.jar -d build/native-tests @files @tests
 if($LASTEXITCODE -ne 0){throw 'Native test compilation failed'}
 foreach($name in @('BatchEngineTest','RemoteBatchEngineTest','ArtistCacheTest','AlbumIndexTest','ForegroundSessionTest','AnimeWorkflowTest','ErrorSummaryTest')){
  & (Join-Path $bin 'java.exe') -cp 'build/native-tests;test-lib/json-tests.jar' ('com.comfymobile.app.'+$name)
  if($LASTEXITCODE -ne 0){throw ('Test failed: '+$name)}
 }
}finally{Pop-Location}
