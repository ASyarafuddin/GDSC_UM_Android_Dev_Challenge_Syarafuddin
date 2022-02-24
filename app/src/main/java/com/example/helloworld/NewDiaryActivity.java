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
    HandlerThread handlerThread;
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
                if (!s.toString().equals("")){
                    draftHandler.removeCallbacksAndMessages(saveTitleDraftToken);
                    draftHandler.postDelayed(getThreadSavingDraft("draftTitle",s.toString()), saveTitleDraftToken, 1000);
                }
                else{
                    draftHandler.removeCallbacksAndMessages(deleteTitleDraftToken);
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
                if (!s.toString().equals("")){
                    draftHandler.removeCallbacksAndMessages(saveTextDraftToken);
                    draftHandler.postDelayed(getThreadSavingDraft("draftText",s.toString()), saveTextDraftToken, 1000);
                }
                else{
                    draftHandler.removeCallbacksAndMessages(deleteTextDraftToken);
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
                if (!s.toString().equals("")){
                    draftHandler.removeCallbacksAndMessages(saveLocDraftToken);
                    draftHandler.postDelayed(getThreadSavingDraft("draftLoc",s.toString()), saveLocDraftToken, 1000);
                }
                else{
                    draftHandler.removeCallbacksAndMessages(deleteLocDraftToken);
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
                        //enable progress bar
                        progressIndicator.setVisibility(View.VISIBLE);
                        progressIndicator.setProgressCompat(500, true);
                        // we also pass userId to our method to save the diary which belongs to our firebaseUser
                        saveTextDiaryToDatabase(title, note, date, firebaseUser.getUid());
                    }
                } else if (isImageDiary(loc)) {
                    if (firebaseUser != null) {
                        // we need to disable the button from another click in case if user clicks the button 2 times so we dont save the same data again
                        saveDiaryButton.setClickable(false);
                        progressIndicator.setVisibility(View.VISIBLE);
                        progressIndicator.setProgressCompat(300, true);
                        saveImageDiaryToStorage(title, note, loc, date, selectedImage, firebaseUser.getUid());
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

                runOnUiThread(() -> draftStatus.setText("Draft saved"));
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

    @Override
    public void onBackPressed() {
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

    private void saveTextDiaryToDatabase(String title,String note, String date,String userId){
        //based on structure of firebase database we will make a hashmap to save our data
        // hashmap consist of keys and values for example key:title has value as the title string that the user entered.
        //the hashmap key type should be String and value can be any object type such as :boolean,integer,string and ect.
        HashMap<String,Object> diaryHashmap=new HashMap<>(); // create instance of hashmap
        diaryHashmap.put("title",title);
        diaryHashmap.put("note",note);
        diaryHashmap.put("date",date);
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
    private void saveImageDiaryToStorage(String title, String note, String loc, String date, Uri image, String userId){
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
    private void saveImageDiaryToDatabase(String title,String note,String loc, String date,String imageURL,String userId){
        //now the process is the same as we did for textDiary
        HashMap<String,Object> diaryHashmap=new HashMap<>();
        diaryHashmap.put("title",title);
        diaryHashmap.put("note",note);
        diaryHashmap.put("date",date);
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
}
