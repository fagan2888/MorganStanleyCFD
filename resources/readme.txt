-	TWS Configuration:
1.	Global Configuration > API > Settings
2.	Check the box “Enable ActiveX and Socket Client”
3.	UN-check the box “Read-only API”
4.	Make sure the Socket Port is 7496
5.	Check the box “Create API Message log file”
6.	Set Logging Level to “Detail”
7.	Check the box “Allow connections from localhost only” (sorry forgot to mention this over the phone)

8.	Global Configuration > API > Precautions
9.	Check the box “Bypass order precautions for API orders”

Suggestion:
Go to Global Configuration > Orders > Settings and set Leave filled order on screen for 0 seconds and Leave cnacelled orders on screen for 0 seconds

-	Running MorganStanleyCFD.jar

1.	Download the latest release of MorganStanleyCFD.zip file from https://github.com/sjinib/MorganStanleyCFD/releases/
2.	Right click on the MorganStanleyCFD.zip file and then extract the zip file to MorganStanleyCFD folder
3.	Click on ‘Start’ button on your windows system and search for “cmd”, choose ‘Command Prompt’ or 'cmd.exe'
4.	Use the ‘cd’ command to navigate to %YOURDIRECTORY%/MorganStanleyCFD/dist. ‘cd [folder name]’ will navigate to that folder, and ‘cd ..’ will go back to the parent folder. If you need to switch hard disk say from C drive to D drive, just type in ‘d:’
5.	Once you navigate to the dist folder, you can use command ‘dir’ to verify that you can see MorganStanleyCDF.jar file there
6.	Use the command ‘java –jar MorganStanleyCFD.jar’ to run the program. This requires you have java installed on your computer and added to the system PATH. You can check if your java is installed using command ‘java –version’. 
