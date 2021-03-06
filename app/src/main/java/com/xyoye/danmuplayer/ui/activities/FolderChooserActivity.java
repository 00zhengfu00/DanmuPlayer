package com.xyoye.danmuplayer.ui.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.xyoye.danmuplayer.R;
import com.xyoye.danmuplayer.ui.adpter.FolderChooserAdapter;
import com.xyoye.danmuplayer.bean.FolderChooserInfo;
import com.xyoye.danmuplayer.database.SharedPreferencesHelper;
import com.xyoye.danmuplayer.utils.GetFileName;
import com.xyoye.danmuplayer.utils.HorizontalDividerItemDecoration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * 文件选择界面
 */
public class FolderChooserActivity extends AppCompatActivity implements View.OnClickListener{
    private final static int CONNECT_TYPE = 11;
    private final static int SELECT_SMBFILE = 12;

    @BindView(R.id.title_left)
    ImageView titleLeft;        //标题栏左边按钮
    @BindView(R.id.title_right)
    ImageView titleRight;       //标题栏右边按钮
    @BindView(R.id.title_text)
    TextView titleText;         // 标题栏title
    @BindView(R.id.save_path)
    TextView savePath;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    @BindView(R.id.ll_loading)
    LinearLayout llLoading;
    @BindView(R.id.local_network)
    TextView localNetwork;      //局域网按钮
    SharedPreferencesHelper sharedPreferencesHelper;

    //是否为文件夹选择器。true文件夹，false文件
    private boolean isFolderChooser = false;
    private String mimeType = "*/*";
    private String mInitialPath = Environment.getExternalStorageDirectory().getAbsolutePath();

    private FolderChooserAdapter mAdapter;
    private List<FolderChooserInfo> mData;

    private File parentFolder;
    private List<FolderChooserInfo> parentContents;
    private boolean canGoUp = true;

    private ExecutorService singleThreadExecutor;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 0:
                    savePath.setText(parentFolder.getAbsolutePath());
                    mData.clear();
                    mData.addAll(getContentsArray());
                    mAdapter.notifyDataSetChanged();

                    llLoading.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    break;
                case CONNECT_TYPE:
                    List<String> fileNames = (List<String>) msg.obj;
                    if (fileNames != null && fileNames.size() > 0){
                        System.out.println(fileNames);
                        Toast.makeText(FolderChooserActivity.this,"连接成功",Toast.LENGTH_LONG).show();
                    }else {
                        System.out.println("连接失败");
                        Toast.makeText(FolderChooserActivity.this,"连接失败",Toast.LENGTH_LONG).show();
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_chooser);
        ButterKnife.bind(this);
        initData();
        initView();
        initListener();
        setData();
    }

    private void initData(){
        SharedPreferencesHelper.init(this);
        sharedPreferencesHelper = SharedPreferencesHelper.getInstance();

        isFolderChooser = getIntent().getBooleanExtra("isFolderChooser", false);
        String mime_Type = getIntent().getStringExtra("mimeType");
        String file_path = getIntent().getStringExtra("file_path");
        mimeType = mime_Type == null ? mimeType : mime_Type;
        singleThreadExecutor = Executors.newSingleThreadExecutor();

        mInitialPath = file_path == null ? mInitialPath : file_path;
        parentFolder = new File(mInitialPath);
        mData = new ArrayList<>();
    }

    private void initView() {

        if (isFolderChooser){
            titleText.setText("请选择目录");
            localNetwork.setVisibility(View.GONE);
        } else{
            titleText.setText("请选择文件");
            localNetwork.setVisibility(View.VISIBLE);
        }
        titleLeft.setVisibility(View.VISIBLE);
        titleRight.setVisibility(View.VISIBLE);
        titleLeft.setImageResource(R.mipmap.ic_arrow_back_white_24dp);
        titleRight.setImageResource(R.mipmap.ic_save_white_24dp);
        savePath.setText(parentFolder.getAbsolutePath());

        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(manager);

        recyclerView.addItemDecoration(new HorizontalDividerItemDecoration.Builder(this)
                .color(0xFFE9E7E8)
                .size(1)
                .build());
        mAdapter = new FolderChooserAdapter(this, mData, new ItemClickCallback() {
            @Override
            public void onClick(View view, int position, FolderChooserInfo info) {
                onSelection(position, info);
            }
        });
        recyclerView.setAdapter(mAdapter);
    }

    private void initListener(){
        titleLeft.setOnClickListener(this);
        titleRight.setOnClickListener(this);
        localNetwork.setOnClickListener(this);
    }

    private void setData(){
        singleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                parentContents = isFolderChooser ? listFiles() : listFiles(mimeType);
                handler.sendEmptyMessage(0);
            }
        });
    }

    /**
     * 判断是否能返回上一层
     */
    private List<FolderChooserInfo> getContentsArray() {
        List<FolderChooserInfo> results = new ArrayList<>();
        if (parentContents == null) {
            if (canGoUp){
                FolderChooserInfo info = new FolderChooserInfo();
                info.setName("...");
                info.setFile(null);
                info.setImage(R.mipmap.back);
                results.add(info);
            }
            return results;
        }
        if (canGoUp){
            FolderChooserInfo info = new FolderChooserInfo();
            info.setName("...");
            info.setFile(null);
            info.setImage(R.mipmap.back);
            results.add(info);
        }
        results.addAll(parentContents);
        return results;
    }

    /**
     * item点击事件
     */
    public void onSelection(int position, FolderChooserInfo info) {
        if (canGoUp && position == 0) {
            if (parentFolder.isFile()) {
                parentFolder = parentFolder.getParentFile();
            }
            parentFolder = parentFolder.getParentFile();
            if (parentFolder.getAbsolutePath().equals("/storage/emulated"))
                parentFolder = parentFolder.getParentFile();
            canGoUp = parentFolder.getParent() != null;
        } else {
            parentFolder = info.getFile();
            canGoUp = true;
            if (parentFolder.getAbsolutePath().equals("/storage/emulated"))
                parentFolder = Environment.getExternalStorageDirectory();
        }
        if (parentFolder.isFile()) {
            ChooserEnd();
        }else{
            llLoading.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            setData();
        }
    }

    /**
     * 获取文件列表
     */
    private List<FolderChooserInfo> listFiles() {
        File[] contents = parentFolder.listFiles();
        List<FolderChooserInfo> results = new ArrayList<>();
        if (contents != null) {
            for (File fi : contents) {
                if (fi.isDirectory()){
                    FolderChooserInfo info = new FolderChooserInfo();
                    info.setName(fi.getName());
                    info.setFile(fi);
                    info.setImage(fileType(fi));
                    results.add(info);
                }
            }
            Collections.sort(results, new FolderSorter());
            return results;
        }
        return null;
    }
    
    /**
     * 获取文件夹列表
     */
    private List<FolderChooserInfo> listFiles(String mimeType) {
        File[] contents = parentFolder.listFiles();
        List<FolderChooserInfo> results = new ArrayList<>();
        if (contents != null) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            for (File fi : contents) {
                if (fi.isDirectory()) {
                    FolderChooserInfo info = new FolderChooserInfo();
                    info.setName(fi.getName());
                    info.setFile(fi);
                    info.setImage(fileType(fi));
                    results.add(info);
                } else {
                    if (fileIsMimeType(fi, mimeType, mimeTypeMap)) {
                        FolderChooserInfo info = new FolderChooserInfo();
                        info.setName(fi.getName());
                        info.setFile(fi);
                        info.setImage(fileType(fi));
                        results.add(info);
                    }
                }
            }
            Collections.sort(results, new FileSorter());
            return results;
        }
        return null;
    }

    /**
     * 判断文件类型
     */
    boolean fileIsMimeType(File file, String mimeType, MimeTypeMap mimeTypeMap) {
        if (mimeType == null || mimeType.equals("*/*")) {
            return true;
        } else {
            // get the file mime type
            String filename = file.toURI().toString();
            int dotPos = filename.lastIndexOf('.');
            if (dotPos == -1) {
                return false;
            }
            String fileExtension = filename.substring(dotPos + 1);
            String fileType = mimeTypeMap.getMimeTypeFromExtension(fileExtension);
            if (fileType == null) {
                return false;
            }
            // check the 'type/subtype' pattern
            if (fileType.equals(mimeType)) {
                return true;
            }
            // check the 'type/*' pattern
            int mimeTypeDelimiter = mimeType.lastIndexOf('/');
            if (mimeTypeDelimiter == -1) {
                return false;
            }
            String mimeTypeMainType = mimeType.substring(0, mimeTypeDelimiter);
            String mimeTypeSubtype = mimeType.substring(mimeTypeDelimiter + 1);
            if (!mimeTypeSubtype.equals("*")) {
                return false;
            }
            int fileTypeDelimiter = fileType.lastIndexOf('/');
            if (fileTypeDelimiter == -1) {
                return false;
            }
            String fileTypeMainType = fileType.substring(0, fileTypeDelimiter);
            if (fileTypeMainType.equals(mimeTypeMainType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文件排序
     */
    private static class FileSorter implements Comparator<FolderChooserInfo> {
        @Override
        public int compare(FolderChooserInfo lhs, FolderChooserInfo rhs) {
            if (lhs.getFile().isDirectory() && !rhs.getFile().isDirectory()) {
                return -1;
            } else if (!lhs.getFile().isDirectory() && rhs.getFile().isDirectory()) {
                return 1;
            } else {
                return lhs.getName().compareTo(rhs.getName());
            }
        }
    }

    /**
     * 文件夹排序
     */
    private static class FolderSorter implements Comparator<FolderChooserInfo> {
        @Override
        public int compare(FolderChooserInfo lhs, FolderChooserInfo rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    }

    public interface ItemClickCallback{
        void onClick(View view, int position, FolderChooserInfo info);
    }
    
    /**
     * 选择完毕
     */
    private void ChooserEnd(){
        File result = parentFolder;
        Intent intent = new Intent();
        intent.putExtra("file_path", result);
        setResult(RESULT_OK, intent);
        finish();
    }


    /**
     * 文件类型展示
     */
    private int fileType(File file){
        int image = R.mipmap.type_file;
        if(file.isDirectory()){
            image = R.mipmap.type_folder;
        }else{
            try {
//            指定文件类型的图标
                String[] token = file.getName().split("\\.");
                String suffix = token[token.length - 1];
                if (suffix.equalsIgnoreCase("txt")) {
                    image = R.mipmap.type_txt;
                } else if (suffix.equalsIgnoreCase("png") || suffix.equalsIgnoreCase("jpg") || suffix.equalsIgnoreCase("jpeg") || suffix.equalsIgnoreCase("gif")) {
                    image = R.mipmap.type_image;
                } else if (suffix.equalsIgnoreCase("mp3")) {
                    image = R.mipmap.type_mp3;
                } else if (suffix.equalsIgnoreCase("mp4") || suffix.equalsIgnoreCase("rmvb") || suffix.equalsIgnoreCase("avi")) {
                    image = R.mipmap.type_video;
                } else if (suffix.equalsIgnoreCase("apk")) {
                    image = R.mipmap.type_apk;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return image;
    }


    /**
     * smb信息dialog展示
     */
    private void showConnectDialog(){
        final AlertDialog.Builder builder;
        View dialog = View.inflate(FolderChooserActivity.this,R.layout.dialog_dlna,null);
        builder = new AlertDialog.Builder(FolderChooserActivity.this).setView(dialog);
        final EditText ip_t = dialog.findViewById(R.id.ip_t);
        final EditText account_t = dialog.findViewById(R.id.account_t);
        final EditText password_t = dialog.findViewById(R.id.password_t);
        String saveIp = sharedPreferencesHelper.getString("ip","smb://");
        String saveAccount = sharedPreferencesHelper.getString("account","");
        String savePassword = sharedPreferencesHelper.getString("password","");
        saveIp = "".equals(saveIp) ? "smb://" : saveIp;
        ip_t.setText(saveIp);
        account_t.setText(saveAccount);
        password_t.setText(savePassword);

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        }).setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new Thread(){
                    @Override
                    public void run(){
                        String ip = ip_t.getText().toString();
                        String account = account_t.getText().toString();
                        String password = password_t.getText().toString();
                        sharedPreferencesHelper.saveString("ip",ip);
                        sharedPreferencesHelper.saveString("account",account);
                        sharedPreferencesHelper.saveString("password",password);
                        String remoteUrl = "".equals(account) ? ip : account+":"+password+"@"+ip;
                        if (!remoteUrl.endsWith("/"))
                            remoteUrl = remoteUrl + "/";

                        Intent intent = new Intent(FolderChooserActivity.this, SmbActivity.class);
                        intent.putExtra("smbUrl",remoteUrl);
                        startActivityForResult(intent,SELECT_SMBFILE);
                    }
                }.start();
            }
        });
        builder.show();
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.title_left:
                FolderChooserActivity.this.finish();
                break;
            case R.id.title_right:
                ChooserEnd();
                break;
            case R.id.local_network:
                showConnectDialog();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode){
                case SELECT_SMBFILE:
                    String file_path = data.getStringExtra("file_path");
                    File result = new File(file_path);
                    Intent intent = new Intent();
                    intent.putExtra("file_path", result);
                    setResult(RESULT_OK, intent);
                    finish();
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
