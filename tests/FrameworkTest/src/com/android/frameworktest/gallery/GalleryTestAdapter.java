package com.android.frameworktest.gallery;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Gallery.LayoutParams;
import android.widget.ImageView.ScaleType;

import com.android.frameworktest.R;

public class GalleryTestAdapter extends BaseAdapter {
    Context mContext;
    final int NO_OF_ITEMS = 10;

    GalleryTestAdapter(Context c) {
        mContext = c;
    }

    public int getCount() {
        return NO_OF_ITEMS;
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout linearLayout = new LinearLayout(mContext);
        linearLayout.setLayoutParams(new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        ImageView imgView = new ImageView(mContext);
        imgView.setLayoutParams(new LayoutParams(150, 100));
        imgView.setPadding(50, 30, 50, 30);
        imgView.setScaleType(ScaleType.CENTER);
        imgView.setImageResource(android.R.drawable.ic_lock_idle_lock);

        TextView textView = new TextView(mContext);
        textView.setLayoutParams(new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setText("Text=" + Integer.toString(position));

        linearLayout.addView(imgView);
        linearLayout.addView(textView);

        return linearLayout;
    }

}

