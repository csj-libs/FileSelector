package com.zlylib.fileselectorlib.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemChildClickListener;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.google.android.material.snackbar.Snackbar;
import com.zlylib.fileselectorlib.R;
import com.zlylib.fileselectorlib.SelectOptions;
import com.zlylib.fileselectorlib.adapter.BreadAdapter;
import com.zlylib.fileselectorlib.adapter.FileListAdapter;
import com.zlylib.fileselectorlib.adapter.SelectSdcardAdapter;
import com.zlylib.fileselectorlib.bean.BreadModel;
import com.zlylib.fileselectorlib.bean.EssFile;
import com.zlylib.fileselectorlib.bean.EssFileCountCallBack;
import com.zlylib.fileselectorlib.bean.EssFileListCallBack;
import com.zlylib.fileselectorlib.core.EssFileCountTask;
import com.zlylib.fileselectorlib.core.EssFileListTask;
import com.zlylib.fileselectorlib.utils.Const;
import com.zlylib.fileselectorlib.utils.FileUtils;
import com.zlylib.titlebarlib.ActionBarCommon;
import com.zlylib.titlebarlib.OnActionBarChildClickListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileSelectorActivity extends AppCompatActivity implements OnItemClickListener, OnItemChildClickListener,
        View.OnClickListener, FileListAdapter.onLoadFileCountListener, EssFileListCallBack, EssFileCountCallBack {

    /*当前目录，默认是SD卡根目录*/
    private String mCurFolder = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
    /*是否刚才切换了SD卡路径*/
    private boolean mHasChangeSdCard = false;
    /*所有可访问存储设备列表*/
    private List<String> mSdCardList;
    private ActionBarCommon abc;
    private RecyclerView mRecyclerView;
    private RecyclerView mBreadRecyclerView;
    private ImageView mImbSelectSdCard;
    private List<String> mSdCardDescList;
    /**
     * 已选中的文件列表
     */
    private ArrayList<EssFile> mSelectedFileList = new ArrayList<>();// 全部信息列表
    private ArrayList<String> mSelectedList = new ArrayList<>();//地址信息列表
    /*当前选中排序方式的位置*/
    private int mSelectSortTypeIndex = 0;
    private BreadAdapter mBreadAdapter;
    private FileListAdapter mAdapter;

    private EssFileListTask essFileListTask;
    private EssFileCountTask essFileCountTask;

    private PopupWindow mSelectSdCardWindow;
    private BroadcastReceiver mScanListener = new BroadcastReceiver() { // from class: com.android.rk.RockExplorer.16
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && (action.equals(Intent.ACTION_MEDIA_MOUNTED) || action.equals(Intent.ACTION_MEDIA_UNMOUNTED))) {
                String tmpCurDir = mCurFolder;
                initSdcard();
                if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    android.util.Log.d("fileSelect", "tmpCurDir：" + tmpCurDir);
                    android.util.Log.d("fileSelect", "mCurFolder：" + mCurFolder);
                    if (!tmpCurDir.startsWith(mCurFolder)) {
                        mSelectedFileList.clear();
                        initBreadView();
                        android.util.Log.d("fileSelect", intent.getDataString());
                        initData();
                    }

                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_file);
        initSdcard();
        initUi();
        initData();
        registBroadcastRec();
    }

    private void initSdcard() {
        mSdCardList = FileUtils.getAllSdPaths(this);
        mSdCardDescList = FileUtils.getAllSdCardList(this, mSdCardList);
        if (!mSdCardList.isEmpty()) {
            mCurFolder = mSdCardList.get(0) + File.separator;
            if (FileUtils.exist(SelectOptions.getInstance().getTargetPath())) {
                mCurFolder = SelectOptions.getInstance().getTargetPath();
            }
        }
        if (mImbSelectSdCard != null) {
            if (!mSdCardList.isEmpty() && mSdCardList.size() > 1) {
                mImbSelectSdCard.setVisibility(View.VISIBLE);
            } else {
                mImbSelectSdCard.setVisibility(View.GONE);
            }
        }

    }

    @SuppressLint("ResourceAsColor")
    private void initUi() {
        abc = findViewById(R.id.abc);
        if (SelectOptions.getInstance().getTitleBg() != 0) {
            abc.setBackgroundColor(getResources().getColor(SelectOptions.getInstance().getTitleBg()));
        }
        if (SelectOptions.getInstance().getTitleColor() != 0) {
            abc.getTitleTextView().setTextColor(getResources().getColor(SelectOptions.getInstance().getTitleColor()));
        }
        if (SelectOptions.getInstance().getTitleLiftColor() != 0) {
            abc.getLeftIconView().setColorFilter(getResources().getColor(SelectOptions.getInstance().getTitleLiftColor()));
        }
        if (SelectOptions.getInstance().getTitleRightColor() != 0) {
            abc.getRightTextView().setTextColor(getResources().getColor(SelectOptions.getInstance().getTitleRightColor()));
        }
        abc.setOnLeftIconClickListener(new OnActionBarChildClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        abc.setOnRightTextClickListener(new OnActionBarChildClickListener() {
            @Override
            public void onClick(View v) {
                if (SelectOptions.getInstance().isOnlySelectFolder()) {
                    mSelectedList.add(mCurFolder);
                }
                //选中
                if (mSelectedList.isEmpty()) {
                    return;
                }
                //不为空
                Intent result = new Intent();
                result.putStringArrayListExtra(Const.EXTRA_RESULT_SELECTION, mSelectedList);
                setResult(RESULT_OK, result);
                finish();
            }
        });
        if (SelectOptions.getInstance().isOnlyShowFolder()) {
            abc.getRightTextView().setText("选中");
        }
        mRecyclerView = findViewById(R.id.rcv_file_list);
        mBreadRecyclerView = findViewById(R.id.breadcrumbs_view);
        mImbSelectSdCard = findViewById(R.id.imb_select_sdcard);
        mImbSelectSdCard.setOnClickListener(this);
        if (!mSdCardList.isEmpty() && mSdCardList.size() > 1) {
            mImbSelectSdCard.setVisibility(View.VISIBLE);
        }

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new FileListAdapter(new ArrayList<EssFile>());
        mAdapter.setLoadFileCountListener(this);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.onAttachedToRecyclerView(mRecyclerView);
        mAdapter.setOnItemClickListener(this);
        initBreadView();

    }

    private void initBreadView() {
        if (mBreadRecyclerView != null) {
            List<BreadModel> breadModelList = FileUtils.getBreadModeListFromPath(mSdCardList, mSdCardDescList, mCurFolder);
            mBreadRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            mBreadAdapter = new BreadAdapter(breadModelList);
            mBreadRecyclerView.setAdapter(mBreadAdapter);
            mBreadAdapter.onAttachedToRecyclerView(mBreadRecyclerView);
            mBreadAdapter.setOnItemChildClickListener(this);
        }
    }

    private void initData() {
        executeListTask(mSelectedFileList, mCurFolder, SelectOptions.getInstance().getFileTypes(), SelectOptions.getInstance().getSortType());
    }

    private void executeListTask(List<EssFile> essFileList, String queryPath, String[] types, int sortType) {
        essFileListTask = new EssFileListTask(essFileList, queryPath, types, sortType, SelectOptions.getInstance().isOnlyShowFolder(), this);
        essFileListTask.execute();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.imb_select_sdcard) {
            showPopupWindow();
        }
    }

    /**
     * 显示选择SdCard的PopupWindow
     * 点击其他区域隐藏，阴影
     */
    private void showPopupWindow() {
        if (mSelectSdCardWindow != null) {
            mSelectSdCardWindow.showAsDropDown(mImbSelectSdCard);
            return;
        }
        View popView = LayoutInflater.from(this).inflate(R.layout.pop_select_sdcard, null);
        mSelectSdCardWindow = new PopupWindow(popView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mSelectSdCardWindow.setFocusable(true);
        mSelectSdCardWindow.setOutsideTouchable(true);
        RecyclerView recyclerView = popView.findViewById(R.id.rcv_pop_select_sdcard);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        final SelectSdcardAdapter adapter = new SelectSdcardAdapter(mSdCardDescList);
        recyclerView.setAdapter(adapter);
        adapter.onAttachedToRecyclerView(recyclerView);
        adapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapterIn, View view, int position) {
                mSelectSdCardWindow.dismiss();
                mHasChangeSdCard = true;
                executeListTask(mSelectedFileList, FileUtils.getChangeSdCard(adapter.getData().get(position), mSdCardList, mSdCardDescList), SelectOptions.getInstance().getFileTypes(), SelectOptions.getInstance().getSortType());
            }
        });
        mSelectSdCardWindow.showAsDropDown(mImbSelectSdCard);
    }

    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
        if (adapter.equals(mBreadAdapter) && view.getId() == R.id.btn_bread) {
            //点击某个路径时
            String queryPath = FileUtils.getBreadModelListByPosition(mSdCardList, mSdCardDescList, mBreadAdapter.getData(), position);
            if (mCurFolder.equals(queryPath)) {
                return;
            }
            executeListTask(mSelectedFileList, queryPath, SelectOptions.getInstance().getFileTypes(), SelectOptions.getInstance().getSortType());
        }
    }

    @Override
    public void onItemClick(BaseQuickAdapter adapter, View view, final int position) {
        if (adapter.equals(mAdapter)) {
            EssFile item = mAdapter.getData().get(position);

            if (item.isDirectory()) {
                //点击文件夹
                //保存当前的垂直滚动位置
                mBreadAdapter.getData().get(mBreadAdapter.getData().size() - 1).setPrePosition(mRecyclerView.computeVerticalScrollOffset());
                executeListTask(mSelectedFileList, mCurFolder + item.getName() + File.separator, SelectOptions.getInstance().getFileTypes(), SelectOptions.getInstance().getSortType());
            } else {
                //选中某文件后，判断是否只能选择文件夹
                if (SelectOptions.getInstance().isOnlySelectFolder()) {
                    if (!item.getFile().isDirectory()) {
                        Snackbar.make(mRecyclerView, "您只能选择文件夹", Snackbar.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (SelectOptions.getInstance().isSingle) {
                    mSelectedFileList.add(item);
                    mSelectedList.add(item.getAbsolutePath());
                    Intent result = new Intent();
                    result.putParcelableArrayListExtra(Const.EXTRA_RESULT_SELECTION, mSelectedFileList);
                    setResult(RESULT_OK, result);
                    super.onBackPressed();
                    return;
                }
                //选中某文件后，判断是否单选
                if (SelectOptions.getInstance().isSingle) {
                    mSelectedFileList.add(item);
                    mSelectedList.add(item.getAbsolutePath());
                    Intent result = new Intent();
                    result.putParcelableArrayListExtra(Const.EXTRA_RESULT_SELECTION, mSelectedFileList);
                    setResult(RESULT_OK, result);
                    super.onBackPressed();
                    return;
                }
                if (mAdapter.getData().get(position).isChecked()) {
                    int index = findFileIndex(item);
                    if (index != -1) {
                        mSelectedFileList.remove(index);
                        mSelectedList.remove(index);
                    }
                } else {
                    if (mSelectedFileList.size() >= SelectOptions.getInstance().maxCount) {
                        //超出最大可选择数量后
                        Snackbar.make(mRecyclerView, "您最多只能选择" + SelectOptions.getInstance().maxCount + "个。", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    mSelectedFileList.add(item);
                    mSelectedList.add(item.getAbsolutePath());
                }
                mAdapter.getData().get(position).setChecked(!mAdapter.getData().get(position).isChecked());
                // mAdapter.notifyItemChanged(position, "");
                mAdapter.notifyDataSetChanged();
                abc.getRightTextView().setText(String.format(getString(R.string.selected_file_count), String.valueOf(mSelectedFileList.size()), String.valueOf(SelectOptions.getInstance().maxCount)));
            }
        }
    }

    /**
     * 查找文件位置
     */
    private int findFileIndex(EssFile item) {
        for (int i = 0; i < mSelectedFileList.size(); i++) {
            if (mSelectedFileList.get(i).getAbsolutePath().equals(item.getAbsolutePath())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onLoadFileCount(int posistion) {
        essFileCountTask = new EssFileCountTask(posistion, mAdapter.getData().get(posistion).getAbsolutePath(), SelectOptions.getInstance().getFileTypes(), SelectOptions.getInstance().isOnlyShowFolder(), this);
        essFileCountTask.execute();
    }

    /**
     * 查找到文件列表后
     *
     * @param queryPath 查询路径
     * @param fileList  文件列表
     */
    @Override
    public void onFindFileList(String queryPath, List<EssFile> fileList) {
        if (fileList.isEmpty()) {
            mAdapter.setEmptyView(R.layout.empty_file_list);
        }
        mCurFolder = queryPath;
        mAdapter.setNewInstance(fileList);
        List<BreadModel> breadModelList = FileUtils.getBreadModeListFromPath(mSdCardList, mSdCardDescList, mCurFolder);
        if (mHasChangeSdCard) {
            mBreadAdapter.setNewInstance(breadModelList);
            mHasChangeSdCard = false;
        } else {
            if (breadModelList.size() > mBreadAdapter.getData().size()) {
                //新增
                List<BreadModel> newList = BreadModel.getNewBreadModel(mBreadAdapter.getData(), breadModelList);
                mBreadAdapter.addData(newList);
            } else {
                //减少
                int removePosition = BreadModel.getRemovedBreadModel(mBreadAdapter.getData(), breadModelList);
                if (removePosition > 0) {
                    mBreadAdapter.setNewData(mBreadAdapter.getData().subList(0, removePosition));
                }
            }
        }

        mBreadRecyclerView.smoothScrollToPosition(mBreadAdapter.getItemCount() - 1);
        //先让其滚动到顶部，然后再scrollBy，滚动到之前保存的位置
        mRecyclerView.scrollToPosition(0);
        int scrollYPosition = mBreadAdapter.getData().get(mBreadAdapter.getData().size() - 1).getPrePosition();
        //恢复之前的滚动位置
        mRecyclerView.scrollBy(0, scrollYPosition);
    }

    @Override
    public void onFindChildFileAndFolderCount(int position, String childFileCount, String childFolderCount) {
        mAdapter.getData().get(position).setChildCounts(childFileCount, childFolderCount);
        // mAdapter.notifyItemChanged(position, "childCountChanges");
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (!FileUtils.canBackParent(mCurFolder, mSdCardList)) {
            super.onBackPressed();
            return;
        }
        executeListTask(mSelectedFileList, new File(mCurFolder).getParentFile().getAbsolutePath() + File.separator, SelectOptions.getInstance().getFileTypes(), SelectOptions.getInstance().getSortType());
    }

    public void registBroadcastRec() {
        IntentFilter f = new IntentFilter();
        f.addAction("android.intent.action.MEDIA_MOUNTED");
        f.addAction("android.intent.action.MEDIA_UNMOUNTED");
        f.addAction("android.os.storage.action.VOLUME_STATE_CHANGED");
        f.addAction("android.intent.action.MEDIA_BAD_REMOVAL");
        f.addDataScheme("file");
        registerReceiver(this.mScanListener, f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: 2019/3/12 暂时移除
//        EventBus.getDefault().unregister(this);
        unregisterReceiver(this.mScanListener);
        if (essFileListTask != null) {
            essFileListTask.cancel(true);
        }
        if (essFileCountTask != null) {
            essFileCountTask.cancel(true);
        }

    }


}
