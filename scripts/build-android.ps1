param([string[]]$Tasks=@(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug'),[string]$JavaHome='',[string]$SdkPath='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if (-not $JavaHome) { $JavaHome=$env:JAVA_HOME }
if (-not $JavaHome -and (Test-Path 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe')) { $JavaHome='C:/Program Files/Android/Android Studio/jbr' }
$javaExe=Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path $javaExe)) { throw 'Set JAVA_HOME or pass -JavaHome' }
if ($SdkPath) { "sdk.dir=$($SdkPath.Replace('\','/').Replace(':','\:'))" | Set-Content (Join-Path $projectRoot 'android/local.properties') }
$env:GRADLE_USER_HOME=Join-Path $projectRoot '.tools/gradle-home'
$wrapperJar=Join-Path $projectRoot 'android/gradle/wrapper/gradle-wrapper.jar'
$launcherJar=Join-Path $projectRoot '.tools/gradle-9.3.1/lib/gradle-gradle-cli-main-9.3.1.jar'
if (Test-Path $launcherJar) {
    & $javaExe --enable-native-access=ALL-UNNAMED -classpath $launcherJar org.gradle.launcher.GradleMain -p (Join-Path $projectRoot 'android') @Tasks --console=plain --max-workers=4
} elseif (Test-Path $wrapperJar) {
    & $javaExe --enable-native-access=ALL-UNNAMED -classpath $wrapperJar org.gradle.wrapper.GradleWrapperMain -p (Join-Path $projectRoot 'android') @Tasks --console=plain --max-workers=4
} else {
    throw 'Install Gradle 9.3.1 or generate the wrapper first'
}
if ($LASTEXITCODE -ne 0) { throw 'Android build/check failed' }
