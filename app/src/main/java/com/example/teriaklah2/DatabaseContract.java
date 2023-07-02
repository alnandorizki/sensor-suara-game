package com.example.teriaklah2;

import android.provider.BaseColumns;

public final class DatabaseContract {

    private DatabaseContract() {}

    public static class ScoreEntry implements BaseColumns {
        public static final String TABLE_NAME = "scores";
        public static final String COLUMN_PLAYER_NAME = "player_name";
        public static final String COLUMN_SCORE = "score";
    }
}
