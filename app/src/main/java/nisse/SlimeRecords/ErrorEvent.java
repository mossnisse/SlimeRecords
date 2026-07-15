package nisse.SlimeRecords;

import java.util.function.Consumer;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;

/**
 * One-shot user-visible error channel between a ViewModel and its screen.
 * A posted message survives rotation until an active observer consumes it,
 * and is cleared on delivery so it is never shown twice.
 */
final class ErrorEvent {
    private final MutableLiveData<String> message = new MutableLiveData<>();

    /** Posts a message from any thread. */
    void post(String text) {
        message.postValue(text);
    }

    /** Drops any message that has been posted but not yet consumed. */
    void clear() {
        message.setValue(null);
    }

    /** Delivers each posted message once, on the main thread. */
    void observe(LifecycleOwner owner, Consumer<String> onMessage) {
        message.observe(owner, text -> {
            if (text != null) {
                message.setValue(null);
                onMessage.accept(text);
            }
        });
    }
}
