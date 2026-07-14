package nisse.SlimeRecords;

import android.content.Context;

import java.util.concurrent.Executor;

import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.SpatialDao;
import nisse.SlimeRecords.data.SpatialDatabase;
import nisse.SlimeRecords.data.UserDatabase;

/** Small dependency boundary used by production code and deterministic instrumentation tests. */
final class AppDependencies {
    interface Provider {
        LocationDao locationDao(Context context);
        SpatialDao spatialDao(Context context);
        Executor executor();
        void resolveGeography(Context context, double latitude, double longitude,
                              GeoResolver.GeoCallback callback);
        long currentTimeMillis();
    }

    private static final Provider PRODUCTION = new Provider() {
        @Override
        public LocationDao locationDao(Context context) {
            return UserDatabase.getInstance(context).locationDao();
        }

        @Override
        public SpatialDao spatialDao(Context context) {
            return SpatialDatabase.getInstance(context).spatialDao();
        }

        @Override
        public Executor executor() {
            return UserDatabase.getDbExecutor();
        }

        @Override
        public void resolveGeography(Context context, double latitude, double longitude,
                                     GeoResolver.GeoCallback callback) {
            GeoResolver.resolve(context, latitude, longitude, callback);
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };

    private static volatile Provider provider = PRODUCTION;

    private AppDependencies() {}

    static Provider get() {
        return provider;
    }

    static void installForTests(Provider testProvider) {
        provider = testProvider;
    }

    static void resetAfterTests() {
        provider = PRODUCTION;
    }
}
