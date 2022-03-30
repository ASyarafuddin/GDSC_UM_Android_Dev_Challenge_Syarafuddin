# GDSC UM Android Development Challenge Syarafuddin

This repository is my submission for GDSC UM Android Development Challenge, where I modify the code provided by GDSC UM. I added 3 new functionalities, which are login using Facebook, login using Twitter and save diary draft.

Setup
- Android Studio Bumblebee 2021.1.1

Dependency
- Refer to app/build.gradle

### A bit guide
1. Login using Facebook
- Utilized Firebase authentication service, refer [here](https://firebase.google.com/docs/auth/android/facebook-login)
- Refer code implementation at app/src/main/java/com/example/helloworld/LoginActivity.java

2. Login using Twitter
- Utilize Firebase authentication service, refer [here](https://firebase.google.com/docs/auth/android/twitter-login)
- Refer code implementation at app/src/main/java/com/example/helloworld/LoginActivity.java

3. Save diary draft
- Related concept: Internal storage in Android, multithreading in Android (I refer to this [article](https://medium.com/@ali.muzaffar/handlerthreads-and-why-you-should-be-using-them-in-your-android-apps-dc8bf1540341)) 
- Refer code implementation at app/src/main/java/com/example/helloworld/NewDiaryActivity.java

### Update after submission

1. New feature: Detect emotion from diary note
- I use ParallelDots Text API for this feature
- You can refer [here](https://apis.paralleldots.com/text_docs/index.html) for the documentation

![image](https://user-images.githubusercontent.com/73981444/156030519-814c31b8-ac99-4f20-9b0d-2f74a161275b.png)
