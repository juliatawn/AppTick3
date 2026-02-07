# AGENTS.md

## Code Preferences
- Use kotlin android sdk 36
- Use the best most modern practices for Android SDK 36 app development
- Make sure it is using the best security practices
- Use the most efficient way and name things in a way that it is understandable
- Always try to use the newest library when adding new code
- Make sure if you see xml file used to check the res layout directory and convert it to jetpack compose if it doesn't already exist. And if you see icons or ic check the res/drawable directory and move to appropriate location. 
- When creating new files try to organize them to a logical location or even an existing folder inside of com.juliacai.apptick
- When making changes or updates be sure to go through all the files in com.juliacai.apptick to make sure there is no duplicated or redundant code

## Test Creation
- Create a thorough suite of tests for validating functionality works, from integration tests to unit tests
- Make sure you do not create duplicate test cases on accident - such as going through each file in the test directory and ensuring there is no duplicates
- Make comments on the purpose of the test and flow of the test
- Add Preview compose test files as well, be sure to organize them into appropriate test folder locations

