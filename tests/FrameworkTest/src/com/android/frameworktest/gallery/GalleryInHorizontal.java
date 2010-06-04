package com.android.frameworktest.gallery;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Gallery;

import com.android.frameworktest.R;

public class GalleryInHorizontal extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gallery_in_horizontal);
        // set gallery parameters
        Gallery gallery = (Gallery)findViewById(R.id.gallery);
        gallery.setAdapter(new GalleryTestAdapter(this));
    }

}

