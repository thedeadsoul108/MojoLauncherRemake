package git.artdeell.mojo.progresskeeper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProgressKeeper {
    private static final Map<String, ProgressState> sProgressStates = new LinkedHashMap<>();
    private static final Map<String, List<ProgressListener>> sProgressListeners = new java.util.HashMap<>();
    private static final List<TaskCountListener> sTaskCountListeners = new ArrayList<>();
    private static final List<GlobalProgressListener> sGlobalListeners = new ArrayList<>();

    public static synchronized void submitProgress(String progressRecord, int progress, int resid, Object... va) {
        ProgressState progressState = sProgressStates.get(progressRecord);
        boolean shouldCallStarted = progressState == null;
        boolean shouldCallEnded = resid == -1 && progress == -1;
        if(shouldCallEnded) {
            shouldCallStarted = false;
            sProgressStates.remove(progressRecord);
        }else if(shouldCallStarted){
            sProgressStates.put(progressRecord, (progressState = new ProgressState()));
        }
        if(shouldCallEnded || shouldCallStarted) updateTaskCount(sProgressStates.size());
        if(progressState != null) {
            progressState.progress = progress;
            progressState.resid = resid;
            progressState.varArg = va;
        }

        List<ProgressListener> progressListeners = sProgressListeners.get(progressRecord);
        if(progressListeners != null)
            for(ProgressListener listener : progressListeners) {
                    if(shouldCallStarted) listener.onProgressStarted();
                    else if(shouldCallEnded) listener.onProgressEnded();
                    else listener.onProgressUpdated(progress, resid, va);
            }
        dispatchGlobalProgress();
    }

    private static void updateTaskCount(int count) {
        synchronized (sTaskCountListeners) {
            Iterator<TaskCountListener> iterator = sTaskCountListeners.iterator();
            while(iterator.hasNext()) {
                if(iterator.next().onUpdateTaskCount(count)) iterator.remove();
            }
        }
    }

    public static synchronized boolean hasProgressKey(String key) {
        return sProgressStates.get(key) != null;
    }

    public static synchronized void addListener(String progressRecord, ProgressListener listener) {
        ProgressState state = sProgressStates.get(progressRecord);
        if(state != null && (state.resid != -1 || state.progress != -1)) {
            listener.onProgressStarted();
            listener.onProgressUpdated(state.progress, state.resid, state.varArg);
        }else{
            listener.onProgressEnded();
        }
        List<ProgressListener> listenerWeakReferenceList = sProgressListeners.get(progressRecord);
        if(listenerWeakReferenceList == null) sProgressListeners.put(progressRecord, (listenerWeakReferenceList = new ArrayList<>()));
        listenerWeakReferenceList.add(listener);
    }

    public static synchronized void removeListener(String progressRecord, ProgressListener listener) {
        List<ProgressListener> listenerWeakReferenceList = sProgressListeners.get(progressRecord);
        if(listenerWeakReferenceList != null) listenerWeakReferenceList.remove(listener);
    }

    public static void addTaskCountListener(TaskCountListener listener) {
        addTaskCountListener(listener, true);
    }
    public static void addTaskCountListener(TaskCountListener listener, boolean runUpdate) {
        if(runUpdate) synchronized (ProgressKeeper.class) {
            listener.onUpdateTaskCount(sProgressStates.size());
        }
        synchronized (sTaskCountListeners) {
            if(!sTaskCountListeners.contains(listener)) sTaskCountListeners.add(listener);
        }
    }
    public static void removeTaskCountListener(TaskCountListener listener) {
        synchronized (sTaskCountListeners) {
            sTaskCountListeners.remove(listener);
        }
    }

    /**
     * Waits until all tasks are done and runs the runnable, or if there were no pending process remaining
     * The runnable runs from the thread that updated the task count last, and it might be the UI thread,
     * so don't put long running processes in it
     * @param runnable the runnable to run when no tasks are remaining
     */
    public static void waitUntilDone(final Runnable runnable) {
        // If we do it the other way the listener would be removed before it was added, which will cause a listener object leak
        if(getTaskCount() == 0) {
            runnable.run();
            return;
        }
        TaskCountListener listener = taskCount -> {
            if(taskCount == 0) {
                runnable.run();
                return true;
            }
            return false;
        };
        addTaskCountListener(listener);
    }

    public static synchronized int getTaskCount() {
        return sProgressStates.size();
    }

    public static boolean hasOngoingTasks() {
        return getTaskCount() > 0;
    }

    /**
     * @return an immutable snapshot of every currently active task, in creation order
     */
    public static synchronized List<TaskSnapshot> getTaskSnapshots() {
        List<TaskSnapshot> snapshots = new ArrayList<>(sProgressStates.size());
        for(Map.Entry<String, ProgressState> entry : sProgressStates.entrySet()) {
            ProgressState state = entry.getValue();
            snapshots.add(new TaskSnapshot(entry.getKey(), state.progress, state.resid, state.varArg));
        }
        return snapshots;
    }

    /**
     * Register a listener that receives the complete task snapshot whenever tasks start, update,
     * or end. This is intended for screens that list every active task (Downloads). The listener
     * may be called on any thread, so it must post before touching views.
     */
    public static void addGlobalProgressListener(GlobalProgressListener listener) {
        synchronized (sGlobalListeners) {
            if(!sGlobalListeners.contains(listener)) sGlobalListeners.add(listener);
        }
        listener.onTasksChanged(getTaskSnapshots());
    }

    public static void removeGlobalProgressListener(GlobalProgressListener listener) {
        synchronized (sGlobalListeners) {
            sGlobalListeners.remove(listener);
        }
    }

    private static void dispatchGlobalProgress() {
        List<GlobalProgressListener> listeners;
        synchronized (sGlobalListeners) {
            if(sGlobalListeners.isEmpty()) return;
            listeners = new ArrayList<>(sGlobalListeners);
        }
        List<TaskSnapshot> snapshots = getTaskSnapshots();
        for(GlobalProgressListener listener : listeners) {
            listener.onTasksChanged(snapshots);
        }
    }

    private static class ProgressState {
        int progress;
        int resid;
        Object[] varArg;
    }

    /**
     * A read-only copy of one active progress record.
     */
    public static class TaskSnapshot {
        public final String key;
        public final int progress;
        public final int resid;
        public final Object[] args;

        private TaskSnapshot(String key, int progress, int resid, Object[] args) {
            this.key = key;
            this.progress = progress;
            this.resid = resid;
            this.args = args;
        }
    }

    public interface GlobalProgressListener {
        void onTasksChanged(List<TaskSnapshot> tasks);
    }
}
