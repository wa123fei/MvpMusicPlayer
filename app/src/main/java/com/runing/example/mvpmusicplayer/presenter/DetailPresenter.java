package com.runing.example.mvpmusicplayer.presenter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.runing.example.mvpmusicplayer.contract.DetailContract;
import com.runing.example.mvpmusicplayer.data.bean.Music;
import com.runing.example.mvpmusicplayer.data.bean.MusicState;
import com.runing.example.mvpmusicplayer.service.MusicService;
import com.runing.example.mvpmusicplayer.util.DataUtils;

import java.util.Collections;
import java.util.List;

import static com.runing.example.mvpmusicplayer.service.MusicService.PlayMode.RANDOM;


/**
 * Created by runing on 2016/5/16.
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
public class DetailPresenter implements DetailContract.Presenter, MusicService.MusicCallBack {

    private DetailContract.View mDetailView;
    /**
     * 音乐列表
     */
    private List<Music> mMusics;

    private List<Music> mRandomMusics = Collections.emptyList();
    /**
     * 服务
     */
    private MusicService mMusicService;
    /**
     * 上一次的模式
     */
    private MusicService.PlayMode mCurrentMode;
    /**
     * 服务连接
     */
    private final ServiceConnection mMusicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMusicService = ((MusicService.Binder) service).getService();
            mMusicService.addMusicCallBack(DetailPresenter.this);
            mDetailView.canAction();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private DetailPresenter(DetailContract.View detailView) {
        this.mDetailView = detailView;
    }

    public static DetailPresenter newInstance(DetailContract.View detailView) {
        DetailPresenter presenter = new DetailPresenter(detailView);
        detailView.setPresenter(presenter);
        return presenter;
    }

    @Override
    public void start() {
        startService();
    }

    /**
     * 开启服务
     */
    private void startService() {
        Context activity = (Context) mDetailView;
        Intent intent = new Intent(activity, MusicService.class);
        activity.bindService(intent, mMusicConnection, Context.BIND_AUTO_CREATE);
        activity.startService(intent);
    }

    @Override
    public void setOnProgressListener(MusicService.OnProgressListener onProgressListener) {
        mMusicService.setOnProgressListener(onProgressListener);
    }

    @Override
    public void seekTo(int progress) {
        mMusicService.seekTo(progress);
    }

    @Override
    public void setPlayMode(MusicService.PlayMode mPlayMode) {
        mMusicService.setPlayMode(mPlayMode);
    }

    @Override
    public void playMusic() {
        mMusicService.play();
    }

    @Override
    public void preMusic() {
        mMusicService.preMusic();
    }

    @Override
    public void nextMusic() {
        mMusicService.nextMusic();
    }

    @Override
    public void pauseMusic() {
        mMusicService.pause();
    }

    @Override
    public void recycleUi() {
        ((Context) mDetailView).unbindService(mMusicConnection);
        mMusicService.cancelProgressListener();
        mMusicService.removeMusicCallBack(this);
    }

    @Override
    public void playSpecified(int position) {
        mMusicService.playSpecified(position);
    }

    @Override
    public void onInitMusicData(List<Music> musics) {
        this.mMusics = musics;
        MusicState musicState = mMusicService.getCurrentMusicState();
        mCurrentMode = musicState.getMode();

        if (mCurrentMode == RANDOM) {
            initMusicPager(mMusicService.getRandomList());
        } else {
            initMusicPager(musics);
        }
        mDetailView.restoreMusic(musicState);
    }

    @Override
    public void onChangeCurrMusic(MusicService.PlayState state, int position) {
        boolean isNoChange = position == MusicService.INDEX_DEFAULT;
        if (!isNoChange) {
            if (mCurrentMode == RANDOM) {
                Music music = mMusics.get(position);
                mDetailView.updateMusic(state, music,
                        DataUtils.findIndex(mRandomMusics, music.getId()));
            } else {
                mDetailView.updateMusic(state, mMusics.get(position), position);
            }
        } else {
            mDetailView.updateMusic(state, null, position);
        }
    }

    @Override
    public void onChangeMusicMode(MusicService.PlayMode mode, List<Music> musics) {
        mDetailView.updatePlayMode(mode);
        mCurrentMode = mode;
        initMusicPager(musics);
    }

    private void initMusicPager(List<Music> musics) {
        Music currMusic = mMusicService.getCurrentMusicState().getMusic();
        //切换为随机模式
        if (mCurrentMode == RANDOM) {
            mRandomMusics = musics;
            //查询当前歌曲在随机列表里的位置
            mDetailView.initMusicPager(musics,
                    DataUtils.findIndex(mRandomMusics, currMusic.getId()));
        }
        //从随机模式还原
        else {
            //查询当前歌曲在初始列表里的位置
            mDetailView.initMusicPager(musics,
                    DataUtils.findIndex(mMusics, currMusic.getId()));
        }
    }
}
