package git.artdeell.mojo.ui;

/**
 * Holds the view-side receiver for task-count updates. A posted update can arrive after the
 * fragment view has been destroyed, so the relay is detached in {@code onDestroyView} and
 * silently drops anything that arrives afterwards.
 */
public final class TaskCountRelay {
    public interface Target {
        void showTaskCount(int count);
    }

    private Target mTarget;

    public void attach(Target target) {
        mTarget = target;
    }

    public void detach() {
        mTarget = null;
    }

    public boolean isAttached() {
        return mTarget != null;
    }

    /** Safe to call from any thread that already guaranteed main-thread delivery. */
    public void dispatch(int count) {
        Target target = mTarget;
        if (target != null) target.showTaskCount(count);
    }
}
