/*
 * Copyright (C) 2013 Thomas Guillem
 *
 * This file is part of aMPD.
 *
 * aMPD is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aMPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with aMPD. If not, see <http://www.gnu.org/licenses/>.
 */

package be.deadba.ampd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.util.Log;

public class MPDConf {
    private final static String TAG = "MPDConf";
    private final static String CONF_FILE = "mpd.conf";
    private final static String STATE_FILE = "state";
    private final static String DB_FILE = "database";
    private final static String STICKER_FILE = "sticker.sql";
    private final static String PLAYLISTS_FILE = "playlists";
    private final static String LOG_FILE_DEFAULT = "androidlog";
    private final static String LOG_LEVEL_DEFAULT = "default";
    private final static String RESTORE_PAUSED_DEFAULT = "yes";
    private final static String PORT_DEFAULT = "6600";
    private final static String AUTO_UPDATE_DEFAULT = "no";


    public final static String DEFAULT_MUSIC_DIRECTORY = Environment.
            getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();

    private static abstract class Entry {
        private final String key;

        public Entry(String key) {
            this.key = key;
        }
        public String getKey() {
            return this.key;
        }
        public abstract String getString();
        public abstract Entries getBlock();
    }

    private static class SimpleEntry extends Entry {
        private final String entry;
        public SimpleEntry(String key, String entry) {
            super(key);
            this.entry = entry;
        }
        @Override
        public String getString() {
            return entry;
        }
        @Override
        public Entries getBlock() {
            return null;
        }
    }

    private static class BlockEntry extends Entry {
        private final Entries entries;
        public BlockEntry(String key, Entries entries) {
            super(key);
            this.entries = entries;
        }
        @Override
        public String getString() {
            return null;
        }
        @Override
        public Entries getBlock() {
            return this.entries;
        }
    }

    private static class Entries extends ArrayList<Entry> {
        private static final long serialVersionUID = 1L;
        public void put(String key, String entry) {
            add(new MPDConf.SimpleEntry(key, entry));
        }
        public void put(String key, Entries entries) {
            add(new MPDConf.BlockEntry(key, entries));
        }
    }

    private static boolean write(Context ctx, Entries entries) {
        boolean success = false;
        StringBuilder conf = new StringBuilder();

        Iterator<MPDConf.Entry> it = entries.iterator();
        while (it.hasNext()) {
            MPDConf.Entry entry = it.next();
            conf.append(entry.getKey());
            if (entry.getString() != null) {
                conf.append(" \"").append(entry.getString()).append("\"\n");
            } else {
                conf.append(" {\n");
                Entries blockEntries = entry.getBlock();
                Iterator<MPDConf.Entry> blockIt = blockEntries.iterator();
                while (blockIt.hasNext()) {
                    MPDConf.Entry blockEntry = blockIt.next();
                    if (blockEntry.getString() == null) {
                        return false;
                    }
                    conf.append(" ").append(blockEntry.getKey())
                        .append(" \"").append(blockEntry.getString()).append("\"\n");
                    blockIt.remove();
                }
                conf.append("}\n");
            }
            it.remove();
        }
        try {
            FileOutputStream fos = ctx.openFileOutput(CONF_FILE, 0);
            fos.write(conf.toString().getBytes());
            fos.close();
            success = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return success;
    }

    public static synchronized void reload(Context ctx) {
        Entries entries = new Entries();

        SharedPreferences sp = getSharedPreferences(ctx);
        String musicDirectory = sp.getString("mpd_music_directory", MPDConf.DEFAULT_MUSIC_DIRECTORY);
        String lastMusicDirectory = sp.getString("last_music_directory", null);
        String port = sp.getString("mpd_port", MPDConf.PORT_DEFAULT);

        Log.d(TAG, "musicDirectory: " + musicDirectory);
        if (lastMusicDirectory == null || !musicDirectory.equals(lastMusicDirectory)) {
            Editor editor = sp.edit();
            editor.putString("last_music_directory", musicDirectory);
            editor.commit();
            Log.d(TAG, "music directory changed: clean db & state");

            File file = ctx.getFileStreamPath(STATE_FILE);
            if (file.exists())
                file.delete();
            file = ctx.getFileStreamPath(DB_FILE);
            if (file.exists())
                file.delete();
        }

        String appPath = ctx.getFilesDir().getAbsolutePath() + "/";
        File f = new File(appPath+PLAYLISTS_FILE);
        if (f.mkdirs() || f.isDirectory())
            entries.put("playlist_directory", appPath+PLAYLISTS_FILE);
        entries.put("db_file", appPath+DB_FILE);
        entries.put("sticker_file", appPath+STICKER_FILE);
        entries.put("state_file",appPath+STATE_FILE);
        entries.put("log_file", LOG_FILE_DEFAULT);

        entries.put("log_level", LOG_LEVEL_DEFAULT);
        entries.put("restore_paused", RESTORE_PAUSED_DEFAULT);
        entries.put("auto_update", AUTO_UPDATE_DEFAULT);

        entries.put("music_directory", musicDirectory);
        entries.put("port", port);

        Entries audioOutputBlock = new Entries();
        audioOutputBlock.put("type", "opensles_android");
        audioOutputBlock.put("name", "OpenSL ES on Android");
        entries.put("audio_output", audioOutputBlock);

        Entries inputBlock = new Entries();
        inputBlock.put("plugin", "curl");
        entries.put("input", inputBlock);

        Entries soundcloudBlock = new Entries();
        soundcloudBlock.put("name", "soundcloud");
        soundcloudBlock.put("enabled", "true");
        soundcloudBlock.put("apikey", "c4c979fd6f241b5b30431d722af212e8");
        entries.put("playlist_plugin", soundcloudBlock);

        write(ctx, entries);
    }

    /**
     * Return a SharedPreferences that can be read/write by different processes.
     * (Main Activity process and MPDService process)
     */
    public static SharedPreferences getSharedPreferences(Context ctx) {
        return ctx.getSharedPreferences("mpdconf", Context.MODE_PRIVATE|Context.MODE_MULTI_PROCESS);
    }

    public static String getPath(Context ctx) {
        return ctx.getFilesDir()+"/"+CONF_FILE;
    }
}
