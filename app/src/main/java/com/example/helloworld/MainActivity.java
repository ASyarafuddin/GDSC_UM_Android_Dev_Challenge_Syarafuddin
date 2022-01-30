package com.example.helloworld;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    FirebaseAuth mAuth;
    private RecyclerView recyclerView;
    private LottieAnimationView lottieAnimationView;
    private TextView noDiaryTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lottieAnimationView = findViewById(R.id.lottie_animation);
        noDiaryTextView = findViewById(R.id.no_diary_text_view);

        //find views in our xml file
        Toolbar toolbar=findViewById(R.id.main_activity_materialToolbar);
        toolbar.inflateMenu(R.menu.main_activity_toolbar_menu);

        ExtendedFloatingActionButton addDiaryButton = findViewById(R.id.add_diary_button);

        mAuth=FirebaseAuth.getInstance();
        FirebaseUser firebaseUser =mAuth.getCurrentUser();

        recyclerView=findViewById(R.id.recycler_view);

        LinearLayoutManager horizontalLayoutManager = new LinearLayoutManager(MainActivity.this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(horizontalLayoutManager);

        if(firebaseUser!=null){
            getDiaries(firebaseUser.getUid());
        }

        toolbar.getMenu().getItem(0).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if(firebaseUser!=null){
                    mAuth.signOut();
                    Intent intent = new Intent(getApplicationContext(),LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); //yg ni lebih kepada how android handle activity dlm task
                    startActivity(intent);
                    finish(); //destroy current context (intent sebelum) context ni tak berapa faham
                }
                return false;
            }
        });

        //action to navigate to our new activity [AddDiary Activity]
        addDiaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(firebaseUser!=null){
                    startActivity(new Intent(getApplicationContext(),NewDiaryActivity.class));
                }
            }
        });

    }

    private void getDiaries(String uid) {

        ArrayList<Diary> diaryList = new ArrayList<>();
        DiaryAdapter diaryAdapter = new DiaryAdapter(MainActivity.this,diaryList);
        recyclerView.setAdapter(diaryAdapter);
        DatabaseReference mRootRef = FirebaseDatabase.getInstance().getReference();

        //get data every time got update
        mRootRef.child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                diaryList.clear();
                if(snapshot.exists()){
                    Log.d("Firebase value listener", "Listener working"); //good practice
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        Diary diary = snap.getValue(Diary.class);
                        diaryList.add(diary);
                        diaryAdapter.notifyDataSetChanged();
                    }
                }
                else{
                    lottieAnimationView.setVisibility(View.VISIBLE);
                    noDiaryTextView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
            });
    }


}