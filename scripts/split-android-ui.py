from pathlib import Path
root=Path(__file__).resolve().parents[1]/'android/app/src/main/java/com/inofarm/jogja'
main=root/'MainActivity.kt'
source=main.read_text(encoding='utf-8')
header=source[:source.index('private val green')].replace('package com.inofarm.jogja','package com.inofarm.jogja.ui',1)
header+='import com.inofarm.jogja.displayTime\n'
start=source.index('@Composable fun OwnerApp')
body=source[start:]
markers=['OwnerApp','Banner','Login','Section','DashboardScreen','RecordCard','Trend','HistoryScreen','Field','RangeFields','DayPicker','SettingsScreen','WorkersScreen']
chunks={}
for i,name in enumerate(markers):
    key='@Composable fun '+name if name=='OwnerApp' else '@Composable private fun '+name
    begin=body.index(key)
    end=body.index('@Composable private fun '+markers[i+1]) if i+1<len(markers) else len(body)
    chunks[name]=body[begin:end].replace('@Composable private fun','@Composable internal fun')
groups={'OwnerApp.kt':['OwnerApp','Login'],'UiComponents.kt':['Banner','Section','RecordCard','Field','RangeFields','DayPicker'],'DashboardScreen.kt':['DashboardScreen','Trend'],'HistoryScreen.kt':['HistoryScreen'],'SettingsScreen.kt':['SettingsScreen'],'WorkersScreen.kt':['WorkersScreen']}
for file,names in groups.items():
    globals='internal val green = Color(0xFF006C49)\ninternal val background = Color(0xFFF5F8F5)\n\n' if file=='UiComponents.kt' else ''
    (root/'ui'/file).write_text(header+'\n'+globals+'\n'.join(chunks[n] for n in names),encoding='utf-8')
main.write_text(source[:start].replace('private val green = Color(0xFF006C49)\nprivate val background = Color(0xFFF5F8F5)\n',''),encoding='utf-8')
