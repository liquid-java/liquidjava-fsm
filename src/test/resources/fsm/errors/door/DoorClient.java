package fsm.errors.door;

public class DoorClient {

    public static void lockTwice() {
        Door twice = new Door();
        twice.lock();
        twice.lock(); // state error: found locked, expected open || closed
    }

    public static void unlockUnknown(boolean flag) {
        Door unknown = new Door(flag);
        unknown.unlock(); // state error: found flag ? open : closed, expected locked
    }

    public static void pushLocked() {
        Door pushed = new Door();
        pushed.lock();
        pushed.push(); // state error: found locked, expected !locked
    }
}
