package nisse.SlimeRecords;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.view.View;

import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import nisse.SlimeRecords.data.CountryEntity;
import nisse.SlimeRecords.data.DistrictEntity;
import nisse.SlimeRecords.data.DistrictGeometryEntity;
import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.ProvinceEntity;
import nisse.SlimeRecords.data.ProvinceGeometryEntity;
import nisse.SlimeRecords.data.SpatialDao;
import nisse.SlimeRecords.data.SpeciesAttributes;
import nisse.SlimeRecords.data.SpeciesReferenceWithAccepted;
import nisse.SlimeRecords.data.UserDatabase;

@RunWith(AndroidJUnit4.class)
public class CriticalUiFlowTest {
    private Context context;
    private UserDatabase database;
    private LocationDao dao;
    private TestProvider provider;

    @Before
    public void setUp() {
        wakeAndUnlockDevice();
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.locationDao();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        provider = new TestProvider();
        AppDependencies.installForTests(provider);
        Intents.init();
    }

    private static void wakeAndUnlockDevice() {
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("input keyevent KEYCODE_WAKEUP")) {
            // Closing the descriptor waits for the shell command to complete.
        } catch (Exception ignored) {
        }
        try (ParcelFileDescriptor ignored = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("wm dismiss-keyguard")) {
        } catch (Exception ignored) {
        }
    }

    @After
    public void tearDown() {
        Intents.release();
        AppDependencies.resetAfterTests();
        // Activity-scoped LiveData may still have a queued Room refresh after the
        // scenario closes. Keep this small in-memory database alive until the
        // instrumentation process exits instead of racing that final refresh.
    }

    @Test
    public void createsRecordAndPersistsResolvedGeography() {
        Intent intent = new Intent(context, RecordDetailActivity.class)
                .putExtra("is_new", true)
                .putExtra("lat", 59.3293)
                .putExtra("lon", 18.0686)
                .putExtra("acc", 4.5f);
        try (ActivityScenario<RecordDetailActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.input_taxon_name)).perform(replaceText("Linnaea borealis"));
            onView(withId(R.id.edit_locality)).perform(replaceText("Test locality"));
            onView(withId(R.id.input_collector)).perform(replaceText("Test collector"), closeSoftKeyboard());
            onView(withId(R.id.btn_save_detail)).perform(scrollTo(), click());
        }

        List<RecordWithPhotos> records = dao.getAllLocationsWithPhotosSync();
        assertEquals(1, records.size());
        ObservationRecord saved = records.get(0).location;
        assertEquals("Linnaea borealis", saved.attributes.taxonName);
        assertEquals("Test locality", saved.locality);
        assertEquals("Sweden", saved.country);
        assertEquals("SE", saved.countryCode);
    }

    @Test
    public void editingRecordPreservesHiddenAttributes() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("show_organism_quantity_field", false)
                .commit();
        ObservationRecord record = new ObservationRecord();
        record.latitude = 59;
        record.longitude = 18;
        record.localTime = "2026-07-14 12:30:00";
        record.note = "Old note";
        record.attributes = new SpeciesAttributes();
        record.attributes.taxonName = "Original taxon";
        record.attributes.organismQuantity = 12;
        long id = dao.insertLocation(record);

        try (ActivityScenario<HistoryActivity> ignored = ActivityScenario.launch(HistoryActivity.class)) {
            onView(withText("Original taxon")).perform(click());
            onView(withId(R.id.input_note)).check(matches(withText("Old note")))
                    .perform(replaceText("Updated note"), closeSoftKeyboard());
            onView(withId(R.id.btn_save_detail)).perform(scrollTo(), click());
        }

        ObservationRecord updated = dao.getLocationByIdSync(id).location;
        assertEquals("Updated note", updated.note);
        assertNotNull(updated.attributes);
        assertEquals(Integer.valueOf(12), updated.attributes.organismQuantity);
    }

    @Test
    public void printScreenRejectsOverflowingRangeBeforeDatabaseWork() {
        try (ActivityScenario<PrintActivity> ignored = ActivityScenario.launch(PrintActivity.class)) {
            onView(withId(R.id.input_range_from))
                    .perform(replaceText("999999999999999999999"), closeSoftKeyboard());
            onView(withId(R.id.btn_generate_label)).perform(click());
            onView(withId(R.id.input_range_from)).check(matches(
                    hasErrorText("Enter a whole number within the supported range.")));
        }
    }

    @Test
    public void importAndExportScreensLaunchWithSafeInitialState() {
        try (ActivityScenario<ImportActivity> ignored = ActivityScenario.launch(ImportActivity.class)) {
            onView(withId(R.id.btn_select_file)).check(matches(isDisplayed()));
            onView(withId(R.id.rbSkip)).check(matches(isDisplayed()));
        }
        try (ActivityScenario<ExportActivity> ignored = ActivityScenario.launch(ExportActivity.class)) {
            onView(withId(R.id.edit_export_format)).check(matches(withText("Standard CSV (Comma)")));
            onView(withId(R.id.btn_start_usb_export)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void exportFormatSelectionSurvivesRecreationAndStatesArePresented() {
        try (ActivityScenario<ExportActivity> scenario = ActivityScenario.launch(ExportActivity.class)) {
            onView(withId(R.id.edit_export_format)).perform(click());
            onView(withText("Artportalen (Semicolon + SWEREF)")).perform(click());
            onView(withId(R.id.edit_export_format)).check(matches(
                    withText("Artportalen (Semicolon + SWEREF)")));

            scenario.recreate();
            onView(withId(R.id.edit_export_format)).check(matches(
                    withText("Artportalen (Semicolon + SWEREF)")));

            scenario.onActivity(activity ->
                    activity.updateUiForState(ExportViewModel.ExportState.ERROR));
            onView(withId(R.id.tv_export_status)).check(matches(withText(R.string.export_error)));
            scenario.onActivity(activity ->
                    activity.updateUiForState(ExportViewModel.ExportState.SUCCESS));
            onView(withId(R.id.tv_export_status)).check(matches(withText(R.string.export_success)));
        }
    }

    @Test
    public void viewModelsRejectConcurrentImportAndExportRequests() {
        QueuedExecutor exportQueue = new QueuedExecutor();
        provider.executor = exportQueue;
        ExportViewModel exportViewModel = new ExportViewModel((Application) context);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            exportViewModel.startExport(ExportFormat.STANDARD_CSV);
            exportViewModel.startExport(ExportFormat.EXCEL_CSV);
        });
        assertEquals(1, exportQueue.size());
        assertEquals(ExportViewModel.ExportState.LOADING,
                exportViewModel.getExportStatus().getValue());

        QueuedExecutor importQueue = new QueuedExecutor();
        provider.executor = importQueue;
        ImportViewModel importViewModel = new ImportViewModel((Application) context);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            importViewModel.startImport(android.net.Uri.EMPTY,
                    ImportProcessor.DuplicateStrategy.SKIP);
            importViewModel.startImport(android.net.Uri.EMPTY,
                    ImportProcessor.DuplicateStrategy.REPLACE);
        });
        assertEquals(1, importQueue.size());
        assertEquals(ImportViewModel.ImportState.LOADING,
                importViewModel.getImportStatus().getValue());
    }

    @Test
    public void printScreenShowsEmptyResultAndReEnablesActions() {
        AtomicReference<View> decorView = new AtomicReference<>();
        try (ActivityScenario<PrintActivity> scenario = ActivityScenario.launch(PrintActivity.class)) {
            scenario.onActivity(activity -> decorView.set(activity.getWindow().getDecorView()));
            onView(withId(R.id.btn_generate_label)).perform(click());
            onView(withText("No specimens found!"))
                    .inRoot(withDecorView(not(is(decorView.get()))))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.btn_generate_label)).check(matches(isEnabled()));
            onView(withId(R.id.btn_share_label)).check(matches(isEnabled()));
        }
    }

    @Test
    public void mainMenuRoutesToImportScreen() {
        intending(hasComponent(ImportActivity.class.getName())).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null));
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText("Import")).perform(click());
            intended(hasComponent(ImportActivity.class.getName()));
        }
    }

    private final class TestProvider implements AppDependencies.Provider {
        private final SpatialDao spatialDao = new EmptySpatialDao();
        private Executor executor = Runnable::run;

        @Override
        public LocationDao locationDao(Context ignored) {
            return dao;
        }

        @Override
        public SpatialDao spatialDao(Context ignored) {
            return spatialDao;
        }

        @Override
        public Executor executor() {
            return executor;
        }

        @Override
        public void resolveGeography(Context ignored, double latitude, double longitude,
                                     GeoResolver.GeoCallback callback) {
            callback.onResolved("Sweden", "Uppland", "Uppsala", "SE");
        }

        @Override
        public long currentTimeMillis() {
            return 1_721_300_000_000L;
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }
    }

    private static final class EmptySpatialDao implements SpatialDao {
        @Override public List<ProvinceGeometryEntity> findProvinceCandidates(int n, int e) {
            return Collections.emptyList();
        }
        @Override public ProvinceEntity getProvinceById(int id) { return null; }
        @Override public List<DistrictGeometryEntity> findDistrictCandidates(int n, int e) {
            return Collections.emptyList();
        }
        @Override public DistrictEntity getDistrictById(int id) { return null; }
        @Override public List<SpeciesReferenceWithAccepted> searchSpeciesWithAccepted(
                String query, String preferredLanguage, List<String> languages, List<String> groups) {
            return Collections.emptyList();
        }
        @Override public CountryEntity getCountryByCode(String code) { return null; }
    }
}
