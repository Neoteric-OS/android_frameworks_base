package com.example.colorchanging;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Trace;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    View view;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        view = this.getWindow().getDecorView();
        view.setBackgroundResource(R.color.gray);
    }

    public void goRed(View v) {
        Trace.endSection();
        Trace.beginSection("red");
        view.setBackgroundResource(R.color.red);
    }

    public void goOrange(View v) {
        Trace.endSection();
        Trace.beginSection("orange");
        view.setBackgroundResource(R.color.orange);
    }

    public void goYellow(View v) {
        Trace.endSection();
        Trace.beginSection("yellow");
        view.setBackgroundResource(R.color.yellow);
    }

    public void goGreen(View v) {
        Trace.endSection();
        Trace.beginSection("green");
        view.setBackgroundResource(R.color.green);
    }

    public void goBlue(View v) {
        Trace.endSection();
        Trace.beginSection("blue");
        view.setBackgroundResource(R.color.blue);
    }

    public void goIndigo(View v) {
        Trace.endSection();
        Trace.beginSection("indigo");
        view.setBackgroundResource(R.color.indigo);
    }

    public void goViolet(View v) {
        Trace.endSection();
        Trace.beginSection("violet");
        view.setBackgroundResource(R.color.violet);
    }

    public void goCyan(View v) {
        Trace.endSection();
        Trace.beginSection("cyan");
        view.setBackgroundResource(R.color.cyan);
    }

    public void goBlack(View v) {
        Trace.endSection();
        Trace.beginSection("black");
        view.setBackgroundResource(R.color.black);
    }
}
