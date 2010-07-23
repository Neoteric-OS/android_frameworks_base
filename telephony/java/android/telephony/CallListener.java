package android.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.ICallListener;

public class CallListener {
	public static final int LISTEN_CALL_ACCEPTED = 1;
	public static final int LISTEN_CALL_CANCELLED = 2;
	public static final int LISTEN_CALL_HELD = 3;
	public static final int LISTEN_CALL_DTMF = 4;

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LISTEN_CALL_ACCEPTED:
                    CallListener.this.onAccepted((String)msg.obj);
                    break;

                case LISTEN_CALL_CANCELLED:
                    CallListener.this.onCancelled((String)msg.obj);
                    break;

                case LISTEN_CALL_HELD:
                    CallListener.this.onHeld((String)msg.obj);
                    break;

                case LISTEN_CALL_DTMF:
                    CallListener.this.onDtmf((String)msg.obj, (char)msg.arg1);
                    break;
				}
            }
        };

    ICallListener callback = new ICallListener.Stub() {
		public void onAccepted(String number) {
            Message.obtain(handler, LISTEN_CALL_ACCEPTED, 0, 0, number).sendToTarget();
			}

		public void onCancelled(String number) {
            Message.obtain(handler, LISTEN_CALL_CANCELLED, 0, 0, number).sendToTarget();
			}

		public void onHeld(String number) {
            Message.obtain(handler, LISTEN_CALL_HELD, 0, 0, number).sendToTarget();
			}

		public void onDtmf(String number, char dtmf) {
            Message.obtain(handler, LISTEN_CALL_DTMF, (int)dtmf, 0, number).sendToTarget();
			}
		};

	/**
	 * Callback invoked when an incoming call is accepted by the user.
	 */
	public void onAccepted(String number) {
		}

	/**
	 * Callback invoked when an incoming call is rejected or an established call is ended by the user.
	 */
	public void onCancelled(String number) {
		}

	/**
	 * Callback invoked when a call is put on hold or unhold by the user.
	 */
	public void onHeld(String number) {
		}

	/**
	 * Callback invoked when a DTMF key is pressed by the user.
	 */
	public void onDtmf(String number, char dtmf) {
		}
	}

