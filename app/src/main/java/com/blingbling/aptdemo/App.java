package com.blingbling.aptdemo;

import android.app.Application;

import com.blingbling.butterknife.api.ButterKnife;

/**
 * Created by BlingBling on 2018/1/4.
 */

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ButterKnife.setDebug(BuildConfig.DEBUG);
    }

}
