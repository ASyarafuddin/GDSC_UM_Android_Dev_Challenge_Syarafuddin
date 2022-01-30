package com.example.helloworld;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;

public class DiaryAdapter extends RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder>{

    private final Context context;
    private final ArrayList<Diary> diariesList;

    public DiaryAdapter(Context context, ArrayList<Diary> diariesList) {
        this.context = context;
        this.diariesList = diariesList;
    }

    @NonNull
    @Override
    public DiaryAdapter.DiaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.diary_layout, parent, false);
        return new DiaryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DiaryAdapter.DiaryViewHolder holder, int position) {
        Diary diary = diariesList.get(position);
        holder.diaryTitle.setText(diary.getTitle());
        holder.diaryNote.setText(diary.getNote());
        holder.diaryDate.setText("Created on" + diary.getDate());
        String diaryType = diary.getType();
        if (diaryType.equals("text")) {
            holder.diaryLoc.setVisibility(View.GONE);
            Glide.with(context).load(R.drawable.login_back_ground_image).transform(new RoundedCorners(15),
                    new CenterCrop()).transition(DrawableTransitionOptions.withCrossFade()).into(holder.diaryImage);
        } else if (diaryType.equals("image")) {
            holder.diaryLoc.setText(diary.getPlaceName());
            String imageURL = diary.getImage();
            if (!imageURL.isEmpty()) {
                Glide.with(context).load(imageURL).transform(new RoundedCorners(15), new CenterCrop())
                        .transition(DrawableTransitionOptions.withCrossFade()).into(holder.diaryImage);
            }
        }
    }

    @Override
    public int getItemCount() {
        return diariesList.size();
    }

    public class DiaryViewHolder extends RecyclerView.ViewHolder {

        private final TextView diaryTitle;
        private final TextView diaryNote;
        private final TextView diaryLoc;
        private final TextView diaryDate;
        private final ImageView diaryImage;


        public DiaryViewHolder(@NonNull View itemView) {
            super(itemView);
            diaryTitle = itemView.findViewById(R.id.adapter_diary_title);
            diaryNote = itemView.findViewById(R.id.adapter_diary_note);
            diaryLoc = itemView.findViewById(R.id.adapter_diary_location);
            diaryImage = itemView.findViewById(R.id.adapter_diary_image);
            diaryDate = itemView.findViewById(R.id.adapter_diary_date);

        }
    }

}
