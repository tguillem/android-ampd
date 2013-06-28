package be.deadba.ampd;
import be.deadba.ampd.IMPDServiceCallback;

interface IMPDService
{
    void start();
    void stop();
    void kill();
    boolean isRunning();
    void registerCallback(IMPDServiceCallback cb);
    void unregisterCallback(IMPDServiceCallback cb);
}
