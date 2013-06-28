package be.deadba.ampd;

interface IMPDServiceCallback
{
    void onStart();
    void onStop(boolean error);
}
