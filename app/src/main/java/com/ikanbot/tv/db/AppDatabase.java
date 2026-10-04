package com.ikanbot.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.ikanbot.tv.App;
import com.ikanbot.tv.bean.Config;
import com.ikanbot.tv.bean.Device;
import com.ikanbot.tv.bean.History;
import com.ikanbot.tv.bean.Keep;
import com.ikanbot.tv.bean.Live;
import com.ikanbot.tv.bean.Site;
import com.ikanbot.tv.bean.Track;
import com.ikanbot.tv.db.dao.ConfigDao;
import com.ikanbot.tv.db.dao.DeviceDao;
import com.ikanbot.tv.db.dao.HistoryDao;
import com.ikanbot.tv.db.dao.KeepDao;
import com.ikanbot.tv.db.dao.LiveDao;
import com.ikanbot.tv.db.dao.SiteDao;
import com.ikanbot.tv.db.dao.TrackDao;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 35;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();
}
