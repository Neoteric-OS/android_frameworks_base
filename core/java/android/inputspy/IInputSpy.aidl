package android.inputspy;


/**
 * Interface for InputSpyManager communicating with InputSpyManagerService.
 * TODO: oneway interface.
 * @hide
 */
interface IInputSpy{
    void startRecording();
    // TODO: auto-save records to file and return file path.
    void stopRecording();
    void startPlaying();
    void stopPlaying();
    void addCheckPoint();
    void analyze();
    void test();
}
