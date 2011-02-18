/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget;

import com.android.internal.R;

import android.content.Context;
import android.util.AttributeSet;
import android.graphics.drawable.Drawable;


/**
 * <p>
 * A checkbox is a specific type of two-states button that can be either
 * checked or unchecked. A example usage of a checkbox inside your activity
 * would be the following:
 * </p>
 *
 * <pre class="prettyprint">
 * public class MyActivity extends Activity {
 *     protected void onCreate(Bundle icicle) {
 *         super.onCreate(icicle);
 *
 *         setContentView(R.layout.content_layout_id);
 *
 *         final CheckBox checkBox = (CheckBox) findViewById(R.id.checkbox_id);
 *         if (checkBox.isChecked()) {
 *             checkBox.setChecked(false);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>See the <a href="{@docRoot}resources/tutorials/views/hello-formstuff.html">Form Stuff
 * tutorial</a>.</p>
 *  
 * <p><strong>XML attributes</strong></p> 
 * <p>
 * See {@link android.R.styleable#CompoundButton CompoundButton Attributes}, 
 * {@link android.R.styleable#Button Button Attributes}, 
 * {@link android.R.styleable#TextView TextView Attributes}, 
 * {@link android.R.styleable#View View Attributes}
 * </p>
 */
public class CheckBox extends CompoundButton {
    private Drawable mLtrBackground;
    private int mStyle;

    public CheckBox(Context context) {
        this(context, null);
    }
    
    public CheckBox(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.checkboxStyle);
    }

    public CheckBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (attrs != null) {
            mStyle = attrs.getStyleAttribute();
        }
        mLtrBackground = getBackground();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (shouldMirror()) {
            switch (mStyle) {
                case com.android.internal.R.attr.starStyle:
                    setBackgroundResource(R.drawable.btn_star_label_background_rtl);
                    break;
                case com.android.internal.R.attr.radioButtonStyle:
                    // fall through
                default:
                    setBackgroundResource(R.drawable.btn_check_label_background_rtl);
                    break;
            }
        } else {
            setBackgroundDrawable(mLtrBackground);
        }
    }
}
