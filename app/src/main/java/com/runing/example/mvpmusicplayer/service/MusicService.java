package com.runing.example.mvpmusicplayer.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseIntArray;
import android.widget.Toast;

import com.runing.example.mvpmusicplayer.R;
import com.runing.example.mvpmusicplayer.contract.NotifyContract;
import com.runing.example.mvpmusicplayer.data.bean.Music;
import com.runing.example.mvpmusicplayer.data.bean.MusicState;
import com.runing.example.mvpmusicplayer.data.biz.MusicGet;
import com.runing.example.mvpmusicplayer.presenter.MusicAWPresenter;
import com.runing.example.mvpmusicplayer.presenter.NotifyPresenter;
import com.runing.example.mvpmusicplayer.ui.MusicNotification;
import com.runing.example.mvpmusicplayer.util.DataUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by runing on 2016/5/13.
 * <p/>
 * This file is part of MvpMusicPlayer.
 * MvpMusicPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * MvpMusicPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with MvpMusicPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */
public final class MusicService extends Service {

    /**
     * 数据保存配置名称
     */
    private static final String SHARE_SAVE_NAME = "music_save";
    /**
     * MusicId数据保存key
     */
    private static final String MUSIC_ID_KAY = "mMusicId";
    /**
     * MusicId数据保存key
     */
    private static final String MUSIC_POSITION_KEY = "mPosition";
    /**
     * PlayMode数据保存key
     */
    private static final String MUSIC_PLAY_MODE_KEY = "mPlayMode";
    /**
     * 默认index
     */
    public static final int INDEX_DEFAULT = -1;
    /**
     * 为外部提供Service
     */
    private static volatile MusicService mMusicService;
    /**
     * 音乐状态回调
     */
    private Set<MusicCallBack> mMusicCallBack = new HashSet<>();
    /**
     * 音乐列表
     */
    private List<Music> mMusics = Collections.emptyList();
    /**
     * 随机音乐列表
     */
    private List<Music> mRandomMusics = new ArrayList<>();
    /**
     * 创建id对position的映射
     */
    private SparseIntArray mIdMapPosition = new SparseIntArray();
    /**
     * 多媒体
     */
    private MediaPlayer mMediaPlayer;
    /**
     * 当前musicId
     */
    private long mMusicId = INDEX_DEFAULT;
    /**
     * 当前index
     */
    private int mIndex = -1;
    /**
     * 当前音乐实例
     */
    private Music mCurrMusic;
    /**
     * 是否处于暂停
     */
    private boolean mIsPause;
    /**
     * 当前进度
     */
    private int mCurrentProgress;
    /**
     * 当前音乐状态
     */
    private final MusicState mMusicState = new MusicState();
    /**
     * 进度回调
     */
    private OnProgressListener mOnProgressListener;
    /**
     * 消息处理
     */
    private Handler mHandler = new Handler();
    /**
     * 当前播放模式
     */
    private PlayMode mCurrentPlayMode = PlayMode.LOOP;
    /**
     * 通知presenter
     */
    private NotifyPresenter mNotifyPresenter;
    /**
     * 小部件presenter
     */
    private MusicAWPresenter mMusicAWPresenter;

    /**
     * 播放状态
     */
    public enum PlayState {
        /**
         * 播放
         */
        PLAY,
        /**
         * 暂停
         */
        PAUSE,
        /**
         * 切换
         */
        SWITCH,
        /**
         * 停止
         */
        STOP
    }

    /**
     * 播放模式
     */
    public enum PlayMode {
        /**
         * 列表循环
         */
        LOOP,
        /**
         * 随机播放
         */
        RANDOM,
        /**
         * 单曲循环
         */
        ONE

    }

    /**
     * 音乐监听
     */
    public interface MusicCallBack {
        /**
         * 初始化音乐数据
         *
         * @param musics 音乐集合
         */
        void onInitMusicData(List<Music> musics);

        /**
         * 改变音乐
         *
         * @param state    播放状态
         * @param position 原始列表位置
         */
        void onChangeCurrMusic(PlayState state, int position);

        /**
         * 播放模式切换
         *
         * @param mode 模式
         */
        void onChangeMusicMode(PlayMode mode, List<Music> musics);
    }

    /**
     * 进度监听
     */
    public interface OnProgressListener {

        /**
         * 开始
         *
         * @param total 总数
         */
        void start(int total);

        /**
         * 进行中
         *
         * @param progress 进度
         */
        void progress(int progress);

    }

    /**
     * 设置播放模式
     *
     * @param mCurrentPlayMode 播放模式
     */
    public void setPlayMode(PlayMode mCurrentPlayMode) {
        this.mCurrentPlayMode = mCurrentPlayMode;
        if (isActive()) {
            if (mCurrentPlayMode == PlayMode.ONE) {
                mMediaPlayer.setLooping(true);
            } else {
                mMediaPlayer.setLooping(false);
            }
        }
        switchModeData();
        notifyPlayMode(mCurrentPlayMode);
    }

    /**
     * 模式数据切换
     */
    private void switchModeData() {
        //切换随机模式
        switch (mCurrentPlayMode) {
            case LOOP: {
                //查询出当前音乐在原始列表里的位置
                mIndex = mIdMapPosition.get((int) mCurrMusic.getId());
                break;
            }
            case RANDOM: {
                Collections.shuffle(mRandomMusics);
                //查询出当前音乐在随机列表里的位置
                mIndex = DataUtils.findIndex(mRandomMusics, mMusicId);
                break;
            }
        }
    }

    /**
     * 进度任务
     */
    private Runnable mProgressRun = new Runnable() {
        @Override
        public void run() {
            if (isActive()) {
                if (mOnProgressListener != null) {
                    mOnProgressListener.progress(
                            mCurrentProgress = mMediaPlayer.getCurrentPosition());
                }
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    /**
     * 指定进度
     *
     * @param progress 进度
     * @throws IllegalArgumentException 进度超过限制
     */
    public void seekTo(int progress) {
        if (isActive()) {
            mCurrentProgress = progress;
            if (progress > mMediaPlayer.getDuration()) {
                throw new IllegalArgumentException("progress overflow!");
            } else {
                mMediaPlayer.seekTo(progress);
            }
        }
    }

    /**
     * 添加音乐回调
     *
     * @param musicCallBack 回调
     */
    public void addMusicCallBack(MusicCallBack musicCallBack) {
        if (musicCallBack != null && hasMusicData()) {
            musicCallBack.onInitMusicData(mMusics);
        }
        mMusicCallBack.add(musicCallBack);
    }

    /**
     * 移除音乐回调
     *
     * @param musicCallBack 回调
     */
    public void removeMusicCallBack(MusicCallBack musicCallBack) {
        mMusicCallBack.remove(musicCallBack);
    }

    /**
     * 移除所有Music回调
     */
    private void removeAllMusicCallBack() {
        if (!mMusicCallBack.isEmpty()) {
            mMusicCallBack.clear();
        }
    }

    /**
     * 设置小部件presenter
     */
    public void setMusicAWPresenter(@Nullable MusicAWPresenter mMusicAWPresenter) {
        this.mMusicAWPresenter = mMusicAWPresenter;
    }

    /**
     * 设置进度监听
     *
     * @param mOnProgressListener 回调
     */
    public void setOnProgressListener(OnProgressListener mOnProgressListener) {
        this.mOnProgressListener = mOnProgressListener;
    }

    /**
     * 取消进度监听
     */
    public void cancelProgressListener() {
        this.mOnProgressListener = null;
    }

    /**
     * 获取全局服务
     *
     * @return 服务实例
     */
    public static MusicService getGlobalService() {
        return mMusicService;
    }

    /**
     * 音乐处于活动状态
     *
     * @return yes or no?
     */
    private boolean isActive() {
        return mIsPause || mMediaPlayer.isPlaying();
    }

    /**
     * 通知音乐数据
     */
    private void notifyMusicData() {
        if (!mMusicCallBack.isEmpty()) {
            for (MusicCallBack callBack : mMusicCallBack) {
                callBack.onInitMusicData(mMusics);
            }
        }
    }

    /**
     * 通知音乐模式切换
     *
     * @param mode 模式
     */
    private void notifyPlayMode(PlayMode mode) {
        if (!mMusicCallBack.isEmpty()) {
            List<Music> result;
            switch (mode) {
                //随机模式
                case RANDOM:
                    result = mRandomMusics;
                    break;
                //正常模式
                default:
                    result = mMusics;
                    break;
            }
            for (MusicCallBack callBack : mMusicCallBack) {
                callBack.onChangeMusicMode(mode, result);
            }
        }
    }

    /**
     * 通知播放状态
     *
     * @param state    状态
     * @param position 位置
     */
    private void notifyPlayState(PlayState state, int position) {
        if (!mMusicCallBack.isEmpty()) {
            if (mCurrentPlayMode == PlayMode.RANDOM) {
                for (MusicCallBack callBack : mMusicCallBack) {
                    callBack.onChangeCurrMusic(state, position != INDEX_DEFAULT ?
                            DataUtils.findIndex(mMusics, mMusicId) : position);
                }
            } else {
                for (MusicCallBack callBack : mMusicCallBack) {
                    callBack.onChangeCurrMusic(state, position);
                }
            }
        }
    }

    /**
     * 播放
     *
     * @return index
     */
    public int play() {
        if (mIsPause) {
            mMediaPlayer.start();
            //改变状态，不改变音乐
            notifyPlayState(PlayState.SWITCH, INDEX_DEFAULT);
            return INDEX_DEFAULT;
        } else {
            playNewMusic();
            //改变状态，并改变音乐
            notifyPlayState(PlayState.SWITCH, getCurrentPosition());
            //设置总进度
            if (mOnProgressListener != null) {
                mOnProgressListener.progress(0);
                mOnProgressListener.start(mMediaPlayer.getDuration());
            }
            //开启通知
            if (mNotifyPresenter != null && !mNotifyPresenter.isNotify()) {
                mNotifyPresenter.start();
                mNotifyPresenter.onChangeCurrMusic(PlayState.PLAY, getCurrentPosition());
            }
        }
        //取消暂停
        mIsPause = false;
        return getCurrentPosition();
    }

    /**
     * 查询初始列表的位置
     *
     * @return 位置
     */
    private int getCurrentPosition() {
        if (mCurrentPlayMode == PlayMode.RANDOM) {
            return mIdMapPosition.get((int) mMusicId);
        } else {
            return mIndex;
        }
    }

    /**
     * 设置当前位置
     *
     * @param position 初始列表位置
     */
    private void setCurrentPosition(int position) {
        if (mCurrentPlayMode == PlayMode.RANDOM) {
            //从随机列表里搜索位置
            mIndex = DataUtils.findIndex(mRandomMusics, mMusics.get(position).getId());
        } else {
            mIndex = position;
        }
    }

    /**
     * 播放新音乐
     */
    private void playNewMusic() {
        mMediaPlayer.reset();
        try {
            mMediaPlayer.setDataSource(mCurrMusic.getUrl());
            mMediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MusicService.this, getResources().getText(R.string.failed_play_music),
                    Toast.LENGTH_SHORT).show();
        }
        //播放新音乐重新设置单曲循环
        if (mCurrentPlayMode == PlayMode.ONE) {
            mMediaPlayer.setLooping(true);
        }
        mMediaPlayer.start();
    }

    /**
     * 暂停
     */
    public void pause() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mIsPause = true;
            notifyPlayState(PlayState.PAUSE, INDEX_DEFAULT);
        }
    }


    /**
     * 下一首
     *
     * @return index
     */
    public int nextMusic() {
        //检查模式
        checkMode(mCurrentPlayMode);
        updateCurrMusic();
        mIsPause = false;
        return play();
    }


    /**
     * 模式检查
     */
    private void checkMode(PlayMode mode) {
        switch (mode) {
            case RANDOM:
            case LOOP:
                mMediaPlayer.setLooping(false);
                // no break
            case ONE: {
                mMediaPlayer.setLooping(true);
                if (mIndex == mMusics.size() - 1) {
                    mIndex = 0;
                } else {
                    mIndex++;
                }
                break;
            }
        }
    }

    /**
     * 上一首
     *
     * @return index
     */
    public int preMusic() {
        if (mIndex == 0) {
            mIndex = mMusics.size() - 1;
        } else {
            mIndex--;
        }
        updateCurrMusic();
        mIsPause = false;
        return play();
    }


    /**
     * 停止音乐
     */
    public void stopMusic() {
        mIsPause = false;
        if (isActive()) {
            mMediaPlayer.stop();
        }
        mCurrentProgress = 0;
        notifyPlayState(PlayState.STOP, INDEX_DEFAULT);
    }

    /**
     * 播放指定音乐
     *
     * @param position 列表位置
     */
    public int playSpecified(int position) {
        if (mCurrentPlayMode == PlayMode.RANDOM) {
            return playRandomSpecified(position);
        } else {
            return playLoopSpecified(position);
        }
    }

    /**
     * 指定位置播放原始列表音乐
     * 可以在随机模式启用状态下使用
     *
     * @param position 原始列表位置
     * @return position
     */
    public int playLoopSpecified(int position) {
        if (getCurrentPosition() != position) {
            setCurrentPosition(position);
            mIsPause = false;
            updateCurrMusic();
            return play();
        }
        return INDEX_DEFAULT;
    }

    /**
     * 指定位置播放随机列表音乐
     * 只能在随机模式启用状态下使用
     *
     * @param position 随机列表位置
     * @return position
     */
    private int playRandomSpecified(int position) {
        if (mIndex != position) {
            mIndex = position;
            mIsPause = false;
            updateCurrMusic();
            return play();
        }
        return INDEX_DEFAULT;
    }

    /**
     * 更新当前音乐
     */
    private void updateCurrMusic() {
        if (mCurrentPlayMode == PlayMode.RANDOM) {
            mCurrMusic = mRandomMusics.get(mIndex);
        } else {
            mCurrMusic = mMusics.get(mIndex);
        }
        mMusicId = mCurrMusic.getId();
    }

    /**
     * 获取随机列表
     */
    public List<Music> getRandomList() {
        if (mCurrentPlayMode == PlayMode.RANDOM) {
            return mRandomMusics;
        } else {
            return null;
        }
    }

    /**
     * 提供当前音乐全部状态
     *
     * @return you know
     */
    public MusicState getCurrentMusicState() {
        //当前音乐
        mMusicState.setMusic(mCurrMusic);
        //当前模式
        mMusicState.setMode(mCurrentPlayMode);
        mMusicState.setPosition(INDEX_DEFAULT);
        //正在播放
        if (isActive()) {
            mMusicState.setTotal(mMediaPlayer.getDuration());
            mMusicState.setPosition(getCurrentPosition());
            mMusicState.setProgress(mCurrentProgress);
        }
        if (mMediaPlayer.isPlaying()) {
            mMusicState.setState(PlayState.PLAY);
        } else {
            mMusicState.setState(PlayState.PAUSE);
        }
        return mMusicState;
    }

    @Override
    public void onCreate() {
        mMusicService = this;
        initMediaPlayer();
        initData();
    }

    /**
     * 初始化
     */
    private void initData() {
        final MusicGet musicGet = new MusicGet();
        musicGet.getMusicList(getContentResolver(), new MusicGet.ScanCallBack() {
            @Override
            public void onOverScanned(List<Music> musics) {
                if (musics.isEmpty()) {
                    return;
                }
                MusicService.this.mMusics = musics;
                deepCopyMusics(mRandomMusics, musics);
                initHistoryMusic();
                restoreMusic();
                notifyMusicData();
                //开启进度循环
                mProgressRun.run();
                //初始化通知
                initNotification();
            }
        });
    }

    /**
     * 初始化通知
     */
    private void initNotification() {
        //创建通知实例
        NotifyContract.View mNotifyView = new MusicNotification(this);
        //创建通知presenter
        mNotifyPresenter = NotifyPresenter.newInstance(this, mNotifyView);
        mNotifyPresenter.start();
        mNotifyPresenter.onChangeCurrMusic(PlayState.STOP, getCurrentPosition());
    }

    /**
     * 还原音乐状态
     */
    private void restoreMusic() {
        if (mCurrentProgress != 0) {
            mIsPause = true;
            try {
                mMediaPlayer.setDataSource(mCurrMusic.getUrl());
                mMediaPlayer.prepare();
                mMediaPlayer.seekTo(mCurrentProgress);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化历史音乐
     */
    private void initHistoryMusic() {
        readMusicHistory();
        mIndex = mIdMapPosition.get((int) mMusicId, INDEX_DEFAULT);
        if (mIndex == INDEX_DEFAULT) {
            setDefaultMusic();
        } else {
            mCurrMusic = mMusics.get(mIndex);
        }
        switchModeData();
    }

    /**
     * 设置默认音乐
     */
    private void setDefaultMusic() {
        mIndex = 0;
        mCurrentProgress = 0;
        updateCurrMusic();
    }

    /**
     * 是否有数据
     *
     * @return has data ?
     */
    private boolean hasMusicData() {
        return !mMusics.isEmpty();
    }

    /**
     * 初始化音频播放
     */
    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                final int position = nextMusic();
                if (mCurrentPlayMode != PlayMode.ONE) {
                    notifyPlayState(PlayState.SWITCH, position);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        mMusicService = null;
        saveMusicHistory();
        recycle();
    }

    /**
     * 保存播放历史
     */
    private void saveMusicHistory() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARE_SAVE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(MUSIC_ID_KAY, mMusicId);
        editor.putInt(MUSIC_POSITION_KEY, mCurrentProgress);
        editor.putString(MUSIC_PLAY_MODE_KEY, mCurrentPlayMode.toString());
        editor.apply();
    }

    /**
     * 读取播放历史
     */
    private void readMusicHistory() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARE_SAVE_NAME, MODE_PRIVATE);
        mMusicId = sharedPreferences.getLong(MUSIC_ID_KAY, INDEX_DEFAULT);
        mCurrentProgress = sharedPreferences.getInt(MUSIC_POSITION_KEY, 0);
        final String mode = sharedPreferences.getString(
                MUSIC_PLAY_MODE_KEY, PlayMode.ONE.toString());
        mCurrentPlayMode = PlayMode.valueOf(mode);
    }

    /**
     * 复制音乐列表
     *
     * @param aims   目标
     * @param source 源
     */
    private void deepCopyMusics(@NonNull List<Music> aims, @NonNull List<Music> source) {
        aims.clear();
        for (int i = 0; i < source.size(); i++) {
            Music music = source.get(i);
            mIdMapPosition.put((int) music.getId(), i);
            aims.add(music.clone());
        }
    }

    /**
     * 回收资源
     */
    private void recycle() {
        //移除音乐进度循环
        mHandler.removeCallbacks(mProgressRun);
        //移除所有音乐回调
        removeAllMusicCallBack();
        //取消进度监听
        cancelProgressListener();
        //回收通知
        if (mNotifyPresenter.isNotify()) {
            mNotifyPresenter.recycleUi();
            mNotifyPresenter = null;
        }
        //回收小部件
        if (mMusicAWPresenter != null) {
            mMusicAWPresenter.recycleUi();
        }
        //释放音频
        mMediaPlayer.release();
    }

    /**
     * 提供Service实例
     */
    public class Binder extends android.os.Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

}
