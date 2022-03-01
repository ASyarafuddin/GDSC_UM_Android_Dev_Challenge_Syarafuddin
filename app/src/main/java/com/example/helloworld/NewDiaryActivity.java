package com.example.helloworld;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.helloworld.ml.LiteModelOnDeviceVisionClassifierLandmarksClassifierAsiaV11;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class NewDiaryActivity extends AppCompatActivity {

    private static final String TAG = "New Diary Activity";

    private ImageView diaryImage;
    private MaterialButton saveDiaryButton;
    private TextInputEditText diaryTitle,diaryNote,diaryLoc;
    private TextView draftStatus;
    private Toolbar toolbar;
    private CircularProgressIndicator progressIndicator;

    //other variables
    private Uri selectedImage;
    private String[] storagePermissions;

    //static variables
    private static final int STORAGE_REQUEST_CODE = 200;
    private static final int IMAGE_PICK_GALLERY_CODE = 400;

    private FirebaseDatabase firebaseDatabase;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;

    //multithreading using HandlerThread
    private HandlerThread handlerThread;
    private Handler draftHandler;
    
    //thread token for handler
    private String saveTitleDraftToken = "1";
    private String deleteTitleDraftToken = "-1";
    private String saveTextDraftToken = "2";
    private String deleteTextDraftToken = "-2";
    private String saveLocDraftToken = "3";
    private String deleteLocDraftToken = "-3";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_diary);

        firebaseAuth = FirebaseAuth.getInstance();

        firebaseDatabase = FirebaseDatabase.getInstance(getString(R.string.firebaseDb_instance));
        databaseReference = firebaseDatabase.getReference(); //refers our firebase real-time database we key-in
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

        //add permission in manifest file as well
        storagePermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        //find views of AddDiary xml file
        progressIndicator = findViewById(R.id.add_diary_progress_bar);
        toolbar = findViewById(R.id.add_diary_toolbar);
        diaryImage = findViewById(R.id.diary_image);
        saveDiaryButton = findViewById(R.id.save_diary_button);
        diaryTitle = findViewById(R.id.diary_title);
        diaryNote = findViewById(R.id.diary_note);
        diaryLoc = findViewById(R.id.diary_location);

        String[] fileList = fileList();
        for(int i = 0; i < fileList().length; i++){
            Log.d(TAG, "File inside internal storage: " + fileList[i]);

            if (fileList[i].equals("draftTitle")) diaryTitle.setText(getDraftFromFile("draftTitle"));
            else if (fileList[i].equals("draftText")) diaryNote.setText(getDraftFromFile("draftText"));
            else if (fileList[i].equals("draftImage")) {
                File imgFile = new File(getFilesDir().getAbsolutePath() + "/draftImage");
                uploadImageToApp(Uri.fromFile(imgFile));
            }
            else if (fileList[i].equals("draftLoc")) {
                diaryLoc.setText(getDraftFromFile("draftLoc"));
                diaryLoc.setVisibility(View.VISIBLE);
            }
        }

        handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();
        draftHandler = new Handler(handlerThread.getLooper());

        //instead of using focusChangeListener, i use TextChangedListener because user may change the text,
        // and if the app is closed at that moment, it is not being saved]

        diaryTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void afterTextChanged(Editable s) {
                draftStatus.setText("Saving...");
                if (!s.toString().isEmpty()){
                    removeRunnable(saveTitleDraftToken, deleteTitleDraftToken);

                    //save draft after user stop typing for 1 sec
                    draftHandler.postDelayed(getThreadSavingDraft("draftTitle",s.toString()), saveTitleDraftToken, 1000);
                }
                else{
                    removeRunnable(saveTitleDraftToken, deleteTitleDraftToken);

                    //delete draft after user stop typing for 1 sec
                    draftHandler.postDelayed(getThreadDeletingDraft("draftTitle"), deleteTitleDraftToken, 1000);
                }
            }
        });
        diaryTitle.requestFocus(); // enable the focus indicator on edit text

        diaryNote.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void afterTextChanged(Editable s) {
                draftStatus.setText("Saving...");
                if (!s.toString().isEmpty()){
                    removeRunnable(saveTextDraftToken, deleteTextDraftToken);

                    //save draft after user stop typing for 1 sec
                    draftHandler.postDelayed(getThreadSavingDraft("draftText",s.toString()), saveTextDraftToken, 1000);
                }
                else{
                    removeRunnable(saveTextDraftToken, deleteTextDraftToken);

                    //delete draft after user stop typing for 1 sec
                    draftHandler.postDelayed(getThreadDeletingDraft("draftText"), deleteTextDraftToken, 1000);
                }
            }
        });

        diaryLoc.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void afterTextChanged(Editable s) {
                draftStatus.setText("Saving...");
                if (!s.toString().isEmpty()){
                    removeRunnable(saveLocDraftToken, deleteLocDraftToken);

                    //save draft after user stop typing for 1 sec
                    draftHandler.postDelayed(getThreadSavingDraft("draftLoc",s.toString()), saveLocDraftToken, 1000);
                }
                else{
                    removeRunnable(saveLocDraftToken, deleteLocDraftToken);

                    //delete draft after user stop typing for 1 sec
                    draftHandler.postDelayed(getThreadDeletingDraft("draftLoc"), deleteLocDraftToken, 1000);
                }
            }
        });

        saveDiaryButton = findViewById(R.id.save_diary_button);
        saveDiaryButton.setOnClickListener(v -> {

            //first need to get all user input in edit texts and if there is an image
            String title = diaryTitle.getText().toString();
            String note = diaryNote.getText().toString();
            String loc = diaryLoc.getText().toString();
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            // before saving to data base we need to check if any compulsory field are empty or not
            // title and note are compulsory not to be empty for both of cases if user wants to save diary with image or without image.
            if (!title.isEmpty() && !note.isEmpty()) {
                // now we check if the user wants to add image diary or textDiary
                if (isTextDiary(loc)) {
                    // another case to be checked before saving data to database is to check that firebase user is not null
                    if (firebaseUser != null) {
                        // we need to disable the button from another click in case if user clicks the button 2 times so we dont save the same data again
                        saveDiaryButton.setClickable(false);

                        progressIndicator.setVisibility(View.VISIBLE);
                        progressIndicator.setProgressCompat(500, true);

                        draftHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                String emotion = detectEmotion(note);
                                Log.d(TAG, "Emotion detected: " + emotion);

                                // we also pass userId to our method to save the diary which belongs to our firebaseUser
                                runOnUiThread(() -> saveTextDiaryToDatabase(title, note, date, emotion, firebaseUser.getUid()) );
                            }
                        });
                    }
                } else if (isImageDiary(loc)) {
                    if (firebaseUser != null) {
                        // we need to disable the button from another click in case if user clicks the button 2 times so we dont save the same data again
                        saveDiaryButton.setClickable(false);
                        progressIndicator.setVisibility(View.VISIBLE);
                        progressIndicator.setProgressCompat(300, true);
                        draftHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                String emotion = detectEmotion(note);
                                Log.d(TAG, "Emotion detected: " + emotion); //Network Exception sebab perform network operation on main thread

                                runOnUiThread(() -> saveImageDiaryToStorage(title, note, loc, date, emotion, selectedImage, firebaseUser.getUid()) );
                            }
                        });
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please fill up all fields", Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(getApplicationContext(), "Please enter both title and note.", Toast.LENGTH_SHORT).show();
            }

        });

        toolbar.getMenu().getItem(0).setOnMenuItemClickListener(item -> {
            displayOptionBuilder();
            return false;
        });
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        draftStatus = findViewById(R.id.draft_status);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handlerThread.quitSafely();
    }
    
    private Runnable getThreadSavingDraft(String fileName, String draftText) {

        return new Runnable(){

            @Override
            public void run() {

                try {

                    FileOutputStream fOut = openFileOutput(fileName ,MODE_PRIVATE);
                    fOut.write(draftText.getBytes());
                    fOut.close();

                    Log.d(TAG, "Saved inside " + fileName);

                }
                catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                runOnUiThread(() -> draftStatus.setText("Draft saved"));
            }

        };

    }

    private Runnable getThreadDeletingDraft(String fileName) {

        return new Runnable(){

            @Override
            public void run() {

                boolean isDraftDeleted = getApplicationContext().deleteFile(fileName);
                Log.d(TAG,fileName + " deleted : "+isDraftDeleted);

                runOnUiThread(() -> {
                    if(isDiaryTextAndImageEmpty()) draftStatus.setText(null);
                    else draftStatus.setText("Draft saved");
                });
            }

        };

    }

    private String getDraftFromFile(String fileName){

        StringBuilder stringBuilder = new StringBuilder();

        try{
            FileInputStream fis = getApplicationContext().openFileInput(fileName);
            InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);

            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                String line = reader.readLine();

                //handle
                if(line != null) stringBuilder.append(line);
            }

            fis.close();
            inputStreamReader.close();

        } catch(FileNotFoundException e){
            Log.e(TAG, fileName + " not found", e);
        } catch (IOException e) {
            Log.e(TAG, "Error reading " + fileName, e);
        }

        Log.d(TAG, fileName + " output: " + stringBuilder);

        if(stringBuilder.toString().equals(null)){

        }
        return stringBuilder.toString();

    }

        private String detectEmotion(String note){
        String[] sentence = note.split("[.]");
        if(sentence.length == 1) {
            try {
                return detectEmotionFromSentence(sentence[0]);
            } catch (Exception e) {
                e.printStackTrace();

                //cannot detect emotion
                return "";
            }
        }

        try{
            return detectEmotionFromMultipleSentence(sentence);
        }catch (Exception e){
            e.printStackTrace();

            //cannot detect emotion
            return "";
        }
    }

    private String detectEmotionFromSentence(String sentence) throws Exception {
        MyAppParallelDots pd = new MyAppParallelDots(getString(R.string.paralleldots_api_key));

        //unpredictable order of emotion inside JSON string
        String JSONEmotionPrediction = pd.emotion(sentence);
        if(JSONEmotionPrediction == null) return "";
        
        String[] emotionName = {"Sad", "Fear", "Happy", "Angry", "Bored", "Excited"};
        int[] emotionIndex = new int[6];
        int[] charIndexAfterFloat = new int[6];

        int firstColonIndex = JSONEmotionPrediction.indexOf(":");

        //index for each emotion name. Consideration: start search from firstColonIndex to make it faster and because the order may be differ each time (which cannot be patterned)
        //if there is pattern, i can use previous emotion index to search next emotion index e.g. Fear -> Happy -> ... , i can start search for Happy from Fear index
        emotionIndex[0] = JSONEmotionPrediction.indexOf("Sad", firstColonIndex);
        emotionIndex[1] = JSONEmotionPrediction.indexOf("Fear", firstColonIndex);
        emotionIndex[2] = JSONEmotionPrediction.indexOf("Happy", firstColonIndex);
        emotionIndex[3] = JSONEmotionPrediction.indexOf("Angry", firstColonIndex);
        emotionIndex[4] = JSONEmotionPrediction.indexOf("Bored", firstColonIndex);
        emotionIndex[5] = JSONEmotionPrediction.indexOf("Excited", firstColonIndex);

        //assign index for comma "," with respect to each word (Sad, Fear, Happy, Angry, Bored, Excited) accordingly
        for (int i = 0; i < emotionIndex.length; i++){
            charIndexAfterFloat[i] = JSONEmotionPrediction.indexOf(",", emotionIndex[i]);
            if(charIndexAfterFloat[i] == -1) charIndexAfterFloat[i] = JSONEmotionPrediction.length() - 2; //use the third last index of JSON to get the last number of the probability, instead of using comma because last probability not have comma
        }

        //extract highest probability
        float significantEmotionProb = 0f;
        String significantEmotion = null;
        for (int i = 0; i < emotionIndex.length; i++){
            int numOfIdxToProbability = 0;
            if(i == 0) numOfIdxToProbability = 5;
            else if (i == 1) numOfIdxToProbability = 6;
            else if (i < emotionIndex.length - 1) numOfIdxToProbability = 7;
            else numOfIdxToProbability = 9;

            String probability = JSONEmotionPrediction.substring(emotionIndex[i] + numOfIdxToProbability, charIndexAfterFloat[i]);
            float currentEmotionProb = Float.parseFloat(probability);

            if(currentEmotionProb > significantEmotionProb){
                significantEmotionProb = currentEmotionProb;
                significantEmotion = emotionName[i];
            }
        }

        Log.d(TAG, "HighestEmotionProb: " + significantEmotionProb + " (" + significantEmotion + ")");

        return significantEmotion;
    }

    private String detectEmotionFromMultipleSentence(String[] sentence) throws Exception {
        MyAppParallelDots pd = new MyAppParallelDots(getString(R.string.paralleldots_api_key));

        String sentenceCollection = "[";
        for (int i = 0; i < sentence.length - 1; i++){
            sentenceCollection += "\"" + sentence[i] + "\", ";
        }
        sentenceCollection += "\"" + sentence[sentence.length - 1] + "\"" + "]";

        JSONArray text_list = (JSONArray) new JSONParser().parse(sentenceCollection);
        String JSONEmotionPrediction = pd.emotion_batch(text_list);
        if(JSONEmotionPrediction == null) return "";
        
        String[] emotionName = {"Sad", "Fear", "Happy", "Angry", "Bored", "Excited"};
        int[][] emotionIndex = new int[sentence.length][6];
        int[][] charIndexAfterFloat = new int[sentence.length][6];

        int firstColonIndex = JSONEmotionPrediction.indexOf(":");

        //index for first word of each emotion
        emotionIndex[0][0] = JSONEmotionPrediction.indexOf(emotionName[0], firstColonIndex);
        emotionIndex[0][1] = JSONEmotionPrediction.indexOf(emotionName[1], firstColonIndex);
        emotionIndex[0][2] = JSONEmotionPrediction.indexOf(emotionName[2], firstColonIndex);
        emotionIndex[0][3] = JSONEmotionPrediction.indexOf(emotionName[3], firstColonIndex);
        emotionIndex[0][4] = JSONEmotionPrediction.indexOf(emotionName[4], firstColonIndex);
        emotionIndex[0][5] = JSONEmotionPrediction.indexOf(emotionName[5], firstColonIndex);

        
        //track comma that seperate between first and second Emotion object and adjust the index to be before the last digit of float
        int commaSeparatorIdx = 0;
        for (int i = 0; i < emotionIndex[0].length; i++) {
            //track comma ",' index here
            charIndexAfterFloat[0][i] = JSONEmotionPrediction.indexOf(",", emotionIndex[0][i]);
            if(charIndexAfterFloat[0][i] > charIndexAfterFloat[0][commaSeparatorIdx]) commaSeparatorIdx = i; //the highest index is the comma separator index
        }
        Log.d(TAG,"CommaSeparatorIdx for first Emotion object: " + charIndexAfterFloat[0][commaSeparatorIdx] + " " + JSONEmotionPrediction.charAt(charIndexAfterFloat[0][commaSeparatorIdx]));
        Log.d(TAG, JSONEmotionPrediction.substring(charIndexAfterFloat[0][commaSeparatorIdx], charIndexAfterFloat[0][commaSeparatorIdx] + 10));
        charIndexAfterFloat[0][commaSeparatorIdx]--; //get the index of '}', which is the index before of last digit for float number

        
        //assign index for comma "," with respect to each emotion OR "}" for the last emotion of each Emotion object
        for (int i = 1; i < emotionIndex.length; i++){

            commaSeparatorIdx = 0;

            for (int j = 0; j < emotionIndex[i].length; j++) {
                //assign index for comma "," with respect to each emotion
                emotionIndex[i][j] = JSONEmotionPrediction.indexOf(emotionName[j], emotionIndex[i-1][j] + 1);
                charIndexAfterFloat[i][j] = JSONEmotionPrediction.indexOf(",", emotionIndex[i][j]);

                //happen when it's the last emotion inside the JSON string. Purpose is to get the char index before the last digit of float
                if(charIndexAfterFloat[i][j] == -1) charIndexAfterFloat[i][j] = JSONEmotionPrediction.length() - 3;

                //track the highest comma index, as it shows that the comma index is at the comma that seperate between Emotion object
                //only run if Emotion object > 2
                if(i < emotionIndex.length - 1 && charIndexAfterFloat[i][j] > charIndexAfterFloat[i][commaSeparatorIdx]) commaSeparatorIdx = j;
            }

            //if the index is not pointing to the last char before float of last emotion inside JSON string,
            //get the index of '}', index before the last digit for float number of last emotion in current Emotion object
            if(commaSeparatorIdx != JSONEmotionPrediction.length() - 3) charIndexAfterFloat[i][commaSeparatorIdx]--;

        }

        
        //sum the probability of each emotion
        float[] emotionProb = new float[6]; //each index represent each emotion
        float totalProbability = 0f;
        for (int i = 0; i < emotionIndex.length; i++){
            for (int j = 0; j < emotionIndex[i].length; j++) {
                int numOfIdxToProbabilityFloat = 0;
                if(j == 0) numOfIdxToProbabilityFloat = 5;
                else if (j == 1) numOfIdxToProbabilityFloat = 6;
                else if (j < emotionIndex[i].length - 1) numOfIdxToProbabilityFloat = 7;
                else numOfIdxToProbabilityFloat = 9;

                String probability = JSONEmotionPrediction.substring(emotionIndex[i][j] + numOfIdxToProbabilityFloat, charIndexAfterFloat[i][j]);
                float currentEmotionProb = Float.parseFloat(probability);
                emotionProb[j] += currentEmotionProb;
                totalProbability += currentEmotionProb;
            }
        }

        
        //extract emotion with highest probability
        float highestEmotionProb = 0f;
        String significantEmotion = null;
        for (int i = 0; i < emotionIndex.length; i++){
            //rescaling the probability in range of 0 - 1, and compare probability between emotions
            if ( (emotionProb[i]/totalProbability) > highestEmotionProb) {
                highestEmotionProb = emotionProb[i];
                significantEmotion = emotionName[i];
            }
        }
        Log.d(TAG, "HighestEmotionProb: " + highestEmotionProb + " (" + significantEmotion + ")");

        return significantEmotion;
    }
    
    @Override
    public void onBackPressed() {
        //no need to prompt to keep/remove draft if no text/image entered in the new diary screen
        if(isDiaryTextAndImageEmpty()) {
            super.onBackPressed();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Do you want to keep the draft?");
        builder.setPositiveButton("Keep", (dialog, which) -> NewDiaryActivity.super.onBackPressed());
        builder.setNeutralButton("Remove", (dialog, which) -> {

            boolean isDraftDeleted = getApplicationContext().deleteFile("draftText");
            Log.d(TAG, "Delete text draft :" + isDraftDeleted);

            isDraftDeleted = getApplicationContext().deleteFile("draftTitle");
            Log.d(TAG, "Delete title draft :" + isDraftDeleted);

            isDraftDeleted =  getApplicationContext().deleteFile("draftLoc");
            Log.d(TAG, "Delete location draft :" + isDraftDeleted);

            isDraftDeleted = getApplicationContext().deleteFile("draftImage");
            Log.d(TAG, "Delete image draft :" + isDraftDeleted);

            NewDiaryActivity.super.onBackPressed();

        });
        builder.create().show();
    }

    private boolean isTextDiary(String loc){
        // if (title and note are not empty) and no image is selected plus the loc is empty means user wants to save only a textDiary
        return selectedImage==null && loc.isEmpty();
    }

    private boolean isImageDiary(String loc){
        // if user is saving a textDiary or imageDiary note and title are compulsory to not be empty
        return !loc.isEmpty() && selectedImage!=null;
    }

    private void saveTextDiaryToDatabase(String title,String note, String date, String emotion, String userId){
        //based on structure of firebase database we will make a hashmap to save our data
        // hashmap consist of keys and values for example key:title has value as the title string that the user entered.
        //the hashmap key type should be String and value can be any object type such as :boolean,integer,string and ect.
        HashMap<String,Object> diaryHashmap=new HashMap<>(); // create instance of hashmap
        diaryHashmap.put("title",title);
        diaryHashmap.put("note",note);
        diaryHashmap.put("date",date);
        diaryHashmap.put("emotion",emotion);
        diaryHashmap.put("type","text");
        //now that we have our hashmap ready we insert our hashmap in database
        // we save the data using the database reference we initialized in onCreate() method
        //each node in the database reference is consider as a child
        // to differentiate the users from each other we need to save their dairies with their specific identical id.
        //the unique identical is wrapped in firebaseUser so we use it as to make node child
        DatabaseReference diaryNode = databaseReference.child(userId).push();
        // the diaryNode is a path or database reference that we want to save the diary in that contains a unique id as well to differentiate each diary from each other.
        String diaryNodeId=diaryNode.getKey(); //getKey() returns the id of that Node we created in line 141
        diaryHashmap.put("diaryId",diaryNodeId); //in case if later when we want to read data from firebase we will need diary Id
        //now finally we save the hashmap that holds our data in to the database to the diaryNode we created
        progressIndicator.setVisibility(View.VISIBLE); // show progress bar
        progressIndicator.setProgressCompat(100,true);
        diaryNode.updateChildren(diaryHashmap).addOnCompleteListener(task -> {
            //we check if task is successfull
            //task is the operation we requested to firebase real-time database to do.
            if(task.isSuccessful()){
                // we show a message to user the diary added successfully and we redirect the user back to MainActivity.class
                progressIndicator.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(),"Diary added Successfully",Toast.LENGTH_SHORT).show();
                Intent intent=new Intent(getApplicationContext(),MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); //We shouldn't let the user to press on back button
                startActivity(intent);
                deleteAllDraft();
                finish(); //we finish the AddDiary activity lifecycle
            }
        }).addOnFailureListener(e -> {
            //in case that any failure happens we show an error message to user.
            // error message is wrapped in e variable in onFailuer method and we use that message.
            Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
            //need to make the saveDiary button back to clickable state
            saveDiaryButton.setClickable(true);
        });
    }
    private void saveImageDiaryToStorage(String title, String note, String loc, String date, String emotion, Uri image, String userId){
        // for image diary we need to save the image into firebase storage first and retrive the link [url] to that storage file
        //only then save the image URL along with title,note and loc to the firebase real-time databasef
        StorageReference storageReference= FirebaseStorage.getInstance().getReference(); //similar to databaseReference
        //now we make a file named as userId in our bucket of firebase storage
        //first child is user folder named as userId and second child is the name of selected image by user
        // lastly we upload the selected image into storage by using putFile()
        System.out.println(image.getLastPathSegment());
        storageReference.child(userId).child(image.getLastPathSegment()).putFile(image)
                .addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                        public void onComplete(@NonNull @NotNull Task<UploadTask.TaskSnapshot> task) {
                        //check first if the operation we requested to firebase storage is successfull
                            if(task.isSuccessful()){
                            // after upload to storage we need to retrieve back the url path that the image is stored
                            //another listener is required to access the file meta data reference in order to retrieve the image URL
                            task.getResult().getMetadata().getReference().getDownloadUrl()
                                .addOnCompleteListener(task1 -> {
                                    if(task1.isSuccessful()){
                                        String imageURL= task1.getResult().toString();
                                        //now we need to save all data in firebase real-time database
                                        saveImageDiaryToDatabase(title,note,loc, date,imageURL,userId);
                                    }
                                }).addOnFailureListener(e -> {
                                    Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
                                    saveDiaryButton.setClickable(true);
                                });

                            }

                        }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
                    saveDiaryButton.setClickable(true);
                });

    }
    private void saveImageDiaryToDatabase(String title,String note,String loc, String date, String emotion,String imageURL,String userId){
        //now the process is the same as we did for textDiary
        HashMap<String,Object> diaryHashmap=new HashMap<>();
        diaryHashmap.put("title",title);
        diaryHashmap.put("note",note);
        diaryHashmap.put("date",date);
        diaryHashmap.put("emotion", emotion);
        diaryHashmap.put("type","image");
        diaryHashmap.put("image",imageURL);
        diaryHashmap.put("placeName",loc);
        DatabaseReference diaryNode = databaseReference.child(userId).push(); //diaryNode
        String diaryNodeId=diaryNode.getKey(); //diary unique Id
        diaryHashmap.put("diaryId",diaryNodeId);
        diaryNode.updateChildren(diaryHashmap).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull @NotNull Task<Void> task) {
                // check the task operation is successfull
                if(task.isSuccessful()){
                    // we show a message to user the diary added successfully and we redirect the user back to MainActivity.class
                    progressIndicator.setVisibility(View.INVISIBLE);
                    Toast.makeText(getApplicationContext(),"Diary added Successfully",Toast.LENGTH_SHORT).show();
                    Intent intent=new Intent(getApplicationContext(),MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); //We shouldn't let the user to press on back button
                    startActivity(intent);
                    deleteAllDraft();
                    finish(); //we finish the AddDiary activity lifecycle
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull @NotNull Exception e) {
                Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
                //need to make the saveDiary button back to clickable state
                saveDiaryButton.setClickable(true);
            }
        });
    }

    private void displayOptionBuilder(){
        //this builder is an alert card it shows options to user to choose from where they want the image from
        String[] options = {"Gallery"};
        //here we only use gallery option if you would like to add camera option add another element to the options array
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Image from");
        builder.setItems(options, (dialog, which) -> {
            //need to check which item from options is selected here we only have one option
            if (which == 0) {
                //first we check if user granted the permission to acces the phone storage
                if (!checkStoragePermission()) {
                    //if is not granted we will request permission
                    requestStoragePermission();
                } else {
                    //when permission is granted we open the gallery
                    pickFromGallery();
                }
            }
        });
        builder.create().show();

    }
    private void pickFromGallery() {
        //send intent to open gallery with a request code
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_GALLERY_CODE);
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE);
    }
    //when user allows or denies the permission this method will be triggered to either grant or reject access
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                //if the access granted then we open gallery
                if (storageAccepted) {
                    pickFromGallery();
                } else {
                    //if not granted we send request again for access and
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
                }
            }
        }

    }

    //when user selects image from gallery this method will be triggered
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        //need to check if the result code is ok and request code we sent is the same as we defined earlier
        if ( resultCode== RESULT_OK && requestCode == IMAGE_PICK_GALLERY_CODE) {
            if (data != null) {
                //the selected image is wrapped in data variable
                selectedImage = data.getData();
                predictLandMark(selectedImage);
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void predictLandMark(Uri selectedImage) {
        try {
            //convert selected image uri to bitmap
            InputStream imageStream=getApplication().getContentResolver().openInputStream(selectedImage);
            Bitmap bitmap =  BitmapFactory.decodeStream(imageStream);

            // need to preprocess the image before feeding to model based on input constraint
            ImageProcessor imageProcessor=new ImageProcessor.Builder()
                    .add(new ResizeOp(321,321, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)) //size 321x321
                    .add(new CastOp(DataType.UINT8)) //the type of image input should be UINT8 which currently our bitmap data type is float32
                    .build();

            //load the model
            LiteModelOnDeviceVisionClassifierLandmarksClassifierAsiaV11 model = LiteModelOnDeviceVisionClassifierLandmarksClassifierAsiaV11.newInstance(this);

            //initialize tensor image object with the required input data type which is UINT8
            TensorImage tensorImage=new TensorImage(DataType.UINT8);

            // Initialize Tensor buffer object in order to feed it to the model
            // based on input contraint the required shape is [1,321,321,3]
            TensorBuffer tensorBuffer=TensorBuffer.createFixedSize(new int[]{1,321,321,3},DataType.UINT8);

            //now we load our bitmap into tensor image we created earlier
            tensorImage.load(bitmap);

            //then need to preprocess the tensor image from bitmap image we load above
            TensorImage processedTensorImage=imageProcessor.process(tensorImage);

            // now need to load the processed tensor image buffer into the tensor buffer object we created in line 400
            tensorBuffer.loadBuffer(processedTensorImage.getBuffer());

            //now we feed the tensorbuffer in the model to make prediction
            LiteModelOnDeviceVisionClassifierLandmarksClassifierAsiaV11.Outputs outputs = model.process(tensorBuffer);
            List<Category> probability=outputs.getProbabilityAsCategoryList();

//          Releases model resources if no longer used.
            model.close();

            // next step is to find the label that has highest probability
            float max=0.0f;
            int bestPredictIndex=0;
            for (int i=0;i<probability.size();i++){
                if(probability.get(i).getScore()>max){
                    max=probability.get(i).getScore();
                    bestPredictIndex=i;
                }
            }

            uploadImageToApp(selectedImage);

            diaryLoc.setVisibility(View.VISIBLE);

            //get highest probability through logging (run in debug mode (?) )
            Log.d("PROB","highest probability:"+probability.get(bestPredictIndex).getScore()); // check in debugger the highest probability

            diaryLoc.setText(probability.get(bestPredictIndex).getLabel());
            diaryLoc.requestFocus(diaryLoc.getTextDirection()); // enable the edit text focus in case if prediction is wrong user must know it is editable by user.

            saveDraftImage(bitmap);

        } catch (IOException e) {
            // if the prediction fails due to any reason
            diaryLoc.setText("Could not predict the name of your selected image");
        }
    }

    private void uploadImageToApp(Uri selectedImage){

        //we use Glide to upload the image in our app
        Glide.with(this)
                .load(selectedImage) //the uri of the image
                .transform(new CenterCrop()) //to fit properly in our image view size
                .transition(DrawableTransitionOptions.withCrossFade()) //with a nice transition for user experience
                .into(diaryImage); //the image view that needs to be place in

        diaryImage.setVisibility(View.VISIBLE);
    }

    private void saveDraftImage(Bitmap bitmap) {

        draftHandler.post(new Runnable(){
            @Override
            public void run() {

                runOnUiThread(() -> draftStatus.setText("Saving..."));

                try {
                    //ada text tunjuk tengah saving/ draft saved

                    FileOutputStream fOut = openFileOutput("draftImage", MODE_PRIVATE);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                    fOut.close();

                    Log.d(TAG, "Image saved inside draftImage");
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                runOnUiThread(() -> draftStatus.setText("Draft saved"));

            }
        });

    }

    private void deleteAllDraft(){

        draftHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean isDraftDeleted = getApplicationContext().deleteFile("draftTitle");
                Log.d(TAG, "Delete draftTitle : " + isDraftDeleted);

                isDraftDeleted = getApplicationContext().deleteFile("draftText");
                Log.d(TAG, "Delete draftText : " + isDraftDeleted);

                isDraftDeleted = getApplicationContext().deleteFile("draftImage");
                Log.d(TAG, "Delete draftImage : " + isDraftDeleted);

                isDraftDeleted = getApplicationContext().deleteFile("draftLoc");
                Log.d(TAG, "Delete draftLoc : " + isDraftDeleted);
            }
        });

    }
    
    private boolean isDiaryTextAndImageEmpty(){
        boolean isImgViewHoldDrawable = diaryImage.getDrawable() != null;
        Log.d(TAG, "diaryImage hold Drawable? : " + isImgViewHoldDrawable);

        if(isImgViewHoldDrawable) return false;

        boolean isDiaryTitleEmpty = diaryTitle.getText().toString().isEmpty();
        boolean isDiaryNoteEmpty = diaryNote.getText().toString().isEmpty();
        boolean isDiaryLocEmpty = diaryLoc.getText().toString().isEmpty();
        Log.d(TAG, "diaryTitle empty?: " + isDiaryTitleEmpty);
        Log.d(TAG, "diaryNote empty?: " + isDiaryNoteEmpty);
        Log.d(TAG, "diaryLoc empty?: " + isDiaryLocEmpty);

        //isImgViewHoldDrawable = false, check whether diaryTitle, diaryNote, diaryLoc are empty
        return isDiaryTitleEmpty && isDiaryNoteEmpty && isDiaryLocEmpty;
    }
    
    /*
    Use to reset timer for respective operation (saving/deleting draft) and
    cancel not related operation (if in process of saving draft, cancel delete operation, vice versa)
     */
    private void removeRunnable(String saveDraftToken, String deleteDraftToken){
        draftHandler.removeCallbacksAndMessages(saveDraftToken);
        draftHandler.removeCallbacksAndMessages(deleteDraftToken);
    }
}
