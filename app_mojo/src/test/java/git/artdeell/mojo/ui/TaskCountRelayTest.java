package git.artdeell.mojo.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Characterization test for the rotation crash: a task-count update posted before rotation can
 * run after the fragment view has been destroyed. The relay must drop it instead of asking a
 * detached fragment for strings.
 */
public class TaskCountRelayTest {

    @Test
    public void updateAfterDetachIsDroppedInsteadOfThrowing() {
        final int[] delivered = {-1};
        TaskCountRelay relay = new TaskCountRelay();
        relay.attach(count -> delivered[0] = count);

        assertTrue(relay.isAttached());
        relay.dispatch(2);
        assertEquals(2, delivered[0]);

        relay.detach();
        assertFalse(relay.isAttached());
        relay.dispatch(7);
        assertEquals(2, delivered[0]);
    }

    @Test
    public void reattachedTargetReceivesNewUpdates() {
        final int[] delivered = {0};
        TaskCountRelay relay = new TaskCountRelay();
        relay.attach(count -> delivered[0] = count);
        relay.detach();
        relay.attach(count -> delivered[0] = count * 10);

        relay.dispatch(3);
        assertEquals(30, delivered[0]);
    }
}
