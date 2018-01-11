package com.blingbling.aptdemo;

import android.widget.TextView;

import com.blingbling.butterknife.annotation.BindView;
import com.blingbling.butterknife.annotation.ContentView;
import com.blingbling.butterknife.annotation.OnClick;

import java.text.SimpleDateFormat;
import java.util.Date;

@ContentView(R.layout.activity_login)
public class LoginActivity extends BaseActivity {

    @BindView(R.id.tv) TextView tv;

    @OnClick({R.id.btn})
    public void click() {
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("HH:mm:ss").format(new Date()))
                .append(" 点击了登录\n")
                .append(tv.getText());
        tv.setText(sb.toString());
    }
}
