call ant war >build.txt

rd "G:\opt\openfire\plugins\openfirejs" /q /s
del "G:\opt\openfire\plugins\openfirejs.war"
copy "D:\Work\Projects\2010.04.21-iTrader\Workspace\openfire_3_7_0\target\openfire\plugins\openfirejs.war" G:\opt\openfire\plugins

del "G:\opt\openfire\logs\*.*"

pause