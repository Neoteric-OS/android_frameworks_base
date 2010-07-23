package android.telephony;

import android.os.RemoteException;
import com.android.internal.telephony.ICallControl;

public class Call {
	private final ICallControl call;

	Call(ICallControl call) {
		this.call = call;
		}
	/**
	 * Signals that the remote party either rejected an outgoing call, cancelled an incoming call, or ended an established call.
	 */
	public void cancel() {
		try {
			call.cancel();
			}
		catch (RemoteException _) {
			}
		}

	/**
	 * Signals that an outgoing call has been accepted by the remote party.
	 * <p>Does nothing if invoked on an incoming or established call.
	 */
	public void accept() {
		try {
			call.accept();
			}
		catch (RemoteException _) {
			}
		}
	}

