package com.lalala.fangs.neutv;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lalala.fang.neutvshow.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.litepal.crud.DataSupport;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import cn.xfangfang.flyme6.TabStrip;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import static org.litepal.LitePalApplication.getContext;

public class MainActivity extends AppCompatActivity {

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private TabStrip tabStrip;
    private ProgressBar progressBar;
    private TextView textWrong;
    private Button btnWrong;
    private ImageView mainImgSetting;
    private ImageView mainImgSearch;


    private List<Live> liveList = new ArrayList<>();
    private List<Type> typeList = new ArrayList<>();
    private int loginTime;
    private BroadcastReceiver broadcastReceiver;
    private DataSaver dataSaver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        utils.getVersion(MainActivity.this);
        initView();
        initClick();
        System.out.println(utils.APP_VERSION);
        checkUpdate();
        new getUpdateLive().execute();
        dataSaver = new DataSaver(MainActivity.this);


        loginTime = dataSaver.getLoginTime();
        if (loginTime < 3) {
            showQQDialog();
        }
        dataSaver.incLoginTime();

    }

    private void initView(){
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        textWrong = (TextView) findViewById(R.id.text_wrong);
        btnWrong = (Button) findViewById(R.id.btn_wrong);
        mainImgSetting = (ImageView) findViewById(R.id.main_img_setting);
        mainImgSearch = (ImageView) findViewById(R.id.main_img_search);
    }

    private void initClick(){
        mainImgSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), SettingActivity.class);
                startActivity(intent);
            }
        });
        mainImgSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    private void showQQDialog(){
        AlertDialog.Builder ab = new AlertDialog.Builder(MainActivity.this);  //(普通消息框)
        ab.setPositiveButton("神奇的功能 点击加群!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                utils.joinQQGroup("OVbiu9aw_bqHtOgXM_fb17lOW0LpzKeA",MainActivity.this);
            }
        });
        ab.setTitle("一共只提醒你三次哦");
        ab.setMessage("欢迎加群\n532607431\n认识作者小哥哥");
        AlertDialog dialog = ab.create();
        dialog.show();
    }

    //取节目单
    private class getUpdateLive extends AsyncTask<String, Integer, Boolean> {
        private static final String TAG = "getUpdateLive";
        private boolean netIsV6;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String updateUrl = "http://hdtv.neu6.edu.cn/hdtv.json";
//            String updateUrl = "http://[2001:250:4800:fe:250:56ff:fe92:c016]/xlxy-TVList.json";
            netIsV6 = utils.isV6(updateUrl);
            Log.e(TAG, "doInBackground: "+netIsV6 );

            OkHttpClient client = new OkHttpClient();
            Request request = new Request
                    .Builder()
                    .url(updateUrl)
                    .addHeader("hdtv",updateUrl)
                    .addHeader("User-Agent", "neutv" + utils.APP_VERSION)
                    .build();
            okhttp3.Response response;
            //每次取节目单前先清除磁盘缓存
            Glide.get(getContext()).clearDiskCache();
            try {
                response = client.newCall(request).execute();
                String res = response.body().string();
                JSONObject dataJson = new JSONObject(res);
                JSONArray data = dataJson.getJSONArray("live");
                JSONArray typeData = dataJson.getJSONArray("type");
                Gson gson = new Gson();
                liveList = gson.fromJson(data.toString(), new TypeToken<ArrayList<Live>>() {
                }.getType());
                typeList = gson.fromJson(typeData.toString(), new TypeToken<ArrayList<Type>>() {
                }.getType());

                //储存type信息
                SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
                editor.putString("type", typeData.toString());
                editor.apply();

            } catch (IOException | JSONException e) {
                e.printStackTrace();
                return false;
            }

            //读取收藏信息
            for (Live i :
                    liveList) {
                Live temp = DataSupport.where("num = ?", String.valueOf(i.getNum())).findFirst(Live.class);
                if (temp == null) {
                    i.save();
                } else {
                    i.setIsFavorite(temp.getIsFavorite());
                }
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            if (aBoolean) {
                if(netIsV6){
                    Snackbar.make(progressBar,"IPv6网络", Snackbar.LENGTH_LONG).show();
                }else{
                    final Snackbar snackbar4 = Snackbar.make(progressBar,"IPv4网络 可能导致网络计费问题", Snackbar.LENGTH_INDEFINITE);
                    snackbar4.setAction("我知道", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            snackbar4.dismiss();
                        }}).show();
                }
                mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
                ArrayList<Fragment> datas = new ArrayList<>();
                ArrayList<String> titles = new ArrayList<>();
                titles.add("收藏");
                datas.add(new FavoriteLiveFragment());

                for (Type i :
                        typeList) {
                    titles.add(i.getName().substring(0, 2));
                    datas.add(new AllLiveFragment(i.getId()));
                }

                mSectionsPagerAdapter.setData(datas, titles);

                mViewPager = (ViewPager) findViewById(R.id.container);
                mViewPager.setAdapter(mSectionsPagerAdapter);

//                mViewPager.setOffscreenPageLimit(2);
                //将收藏放在-1屏
                mViewPager.setCurrentItem(1);

                tabStrip = (TabStrip) findViewById(R.id.tabstrip);
                tabStrip.setViewPager(mViewPager);
                textWrong.setVisibility(View.INVISIBLE);
                btnWrong.setVisibility(View.INVISIBLE);
            } else {
                textWrong.setVisibility(View.VISIBLE);
                btnWrong.setVisibility(View.VISIBLE);
                btnWrong.setClickable(true);
            }
            progressBar.setVisibility(View.INVISIBLE);

        }
    }

    /**
     * 检查软件版本状态
     */
    private void checkUpdate(){
        UpdataAPK.onUpdate(new UpdataAPK.UpdateListener() {
            /**
             * 每次获取更新信息时响应
             * @param version 当前的最新版本
             * @param description 最新版本的说明
             * @param downloadLink 最新版本的下载链接
             */
            @Override
            public void info(final double version, String description, final String downloadLink) {
                if (version > Double.valueOf(utils.APP_VERSION)) {
                    AlertDialog.Builder ab = new AlertDialog.Builder(MainActivity.this);  //(普通消息框)
                    ab.setPositiveButton("更新", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadLink));
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setTitle("直视新版本");
                            request.setDescription(String.valueOf(version));
                            request.addRequestHeader("User-Agent", "neutv" + utils.getVersion(MainActivity.this));
                            File saveFile = new File(Environment.getExternalStorageDirectory(), "neutv" + String.valueOf(version) + ".apk");
                            request.setDestinationUri(Uri.fromFile(saveFile));

                            final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                            final long downloadId = manager.enqueue(request);
                            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                            broadcastReceiver = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    long ID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                                    if (ID == downloadId) {
                                        DownloadManager.Query query = new DownloadManager.Query();
                                        query.setFilterById(downloadId);
                                        query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
                                        Cursor cursor = manager.query(query);
                                        if (!cursor.moveToFirst()) {
                                            cursor.close();
                                            Snackbar.make(progressBar, "下载失败", Snackbar.LENGTH_LONG).show();
                                            return;
                                        }
                                        cursor.close();
                                        Snackbar.make(progressBar, "安装包下载好了", Snackbar.LENGTH_INDEFINITE).
                                                setAction("安装", new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                                                        File saveFile = new File(Environment.getExternalStorageDirectory(), "neutv" + String.valueOf(version) + ".apk");
                                                        installIntent.setDataAndType(Uri.fromFile(saveFile),
                                                                "application/vnd.android.package-archive");
                                                        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                        startActivity(installIntent);
                                                    }
                                                }).show();
                                    }
                                }
                            };
                            registerReceiver(broadcastReceiver, intentFilter);
                        }
                    });
                    ab.setTitle("新版本 " + String.valueOf(version) + " 来了");
                    ab.setMessage(description);
                    AlertDialog dialog = ab.create();
                    dialog.show();
                }
            }

            /**
             * 当获取到无法解析的更新信息时响应
             * @param errorInfo 错误信息
             */
            @Override
            public void error(String errorInfo) {
                AlertDialog.Builder ab = new AlertDialog.Builder(MainActivity.this);
                ab.setTitle("错误消息");
                ab.setMessage(errorInfo);
                AlertDialog dialog = ab.create();
                dialog.show();
            }
        });
    }

    /**
     * 访问网络失败 重新访问
     * @param v
     */
    public void reCall(View v) {
        v.setClickable(false);
        new getUpdateLive().execute();
    }

    private static final String TAG = "MainActivity";

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                Log.e(TAG, "onKeyDown: ok");
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                Log.e(TAG, "onKeyDown: down");
                mViewPager.requestFocus();
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                Log.e(TAG, "onKeyDown: left");
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                Log.e(TAG, "onKeyDown: right");
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                Log.e(TAG, "onKeyDown: up");
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }


}
