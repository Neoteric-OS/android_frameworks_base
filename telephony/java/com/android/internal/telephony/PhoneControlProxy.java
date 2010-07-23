package com.android.internal.telephony;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import android.util.*;

import static com.android.internal.telephony.CommandsInterface.*;
import static com.android.internal.telephony.CallTracker.*;

public class PhoneControlProxy extends IPhoneControl.Stub implements InvocationHandler {
	private static int next = 0;

	public class CallControlProxy extends ICallControl.Stub {
		final ICallListener listener;
		final int index;
		final String number;
		DriverCall.State state;

		CallControlProxy(String number, ICallListener listener) {
			index = ++next;
			this.number = number;
			this.listener = listener;
			}

		public void accept() {
			for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
				CallControlProxy call = iterator.next();
				if (call.index == index) {
					call.state = DriverCall.State.ACTIVE;
					Message result = Message.obtain();
					result.what = EVENT_CALL_STATE_CHANGE;
					AsyncResult.forMessage(result);
					callStateChangedHandler.sendMessage(result);
					return;
					}
				}
			}

		public void cancel() {
			for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
				CallControlProxy call = iterator.next();
				if (call.index == index) {
					iterator.remove();
					Message result = Message.obtain();
					result.what = EVENT_CALL_STATE_CHANGE;
					AsyncResult.forMessage(result);
					callStateChangedHandler.sendMessage(result);
					return;
					}
				}
			}

		void onAccepted() {
			try {
				listener.onAccepted(number);
				}
			catch (RemoteException _) {}
			}

		void onCancelled() {
			try {
				listener.onCancelled(number);
				}
			catch (RemoteException _) {}
			}

		void onHeld() {
			try {
				listener.onHeld(number);
				}
			catch (RemoteException _) {}
			}

		void onDtmf(char dtmf) {
			try {
				listener.onDtmf(number, dtmf);
				}
			catch (RemoteException _) {}
			}
		}

	private static class Mapping {
		final boolean modem;
		final int index;
		DriverCall.State state;

		Mapping(boolean modem, int index) {
			this.modem = modem;
			this.index = index;
			}
		}

	private static final Map<Method, Method> OVERRIDES;
	static {
		Map map = new HashMap<Method, Method>();
		try {
			convert(map, CommandsInterface.class.getMethod("getSignalStrength", Message.class));
			convert(map, CommandsInterface.class.getMethod("hangupConnection", Integer.TYPE, Message.class));
			convert(map, CommandsInterface.class.getMethod("hangupWaitingOrBackground", Message.class));
			convert(map, CommandsInterface.class.getMethod("hangupForegroundResumeBackground", Message.class));
			convert(map, CommandsInterface.class.getMethod("acceptCall", Message.class));
			convert(map, CommandsInterface.class.getMethod("switchWaitingOrHoldingAndActive", Message.class));
			}
		catch (NoSuchMethodException exception) {
			exception.printStackTrace();
			}
		OVERRIDES = Collections.unmodifiableMap(map);
		}
	private static final Map<Integer, String> EVENTS;
	static {
		Map map = new HashMap<Integer, String>();
		map.put(EVENT_POLL_CALLS_RESULT, "EVENT_POLL_CALLS_RESULT");
		map.put(EVENT_CALL_STATE_CHANGE, "EVENT_CALL_STATE_CHANGE");
		map.put(EVENT_REPOLL_AFTER_DELAY, "EVENT_REPOLL_AFTER_DELAY");
		map.put(EVENT_OPERATION_COMPLETE, "EVENT_OPERATION_COMPLETE");
		map.put(EVENT_GET_LAST_CALL_FAIL_CAUSE, "EVENT_GET_LAST_CALL_FAIL_CAUSE");
		map.put(EVENT_SWITCH_RESULT, "EVENT_SWITCH_RESULT");
		map.put(EVENT_RADIO_AVAILABLE, "EVENT_RADIO_AVAILABLE");
		map.put(EVENT_RADIO_NOT_AVAILABLE, "EVENT_RADIO_NOT_AVAILABLE");
		map.put(EVENT_CONFERENCE_RESULT, "EVENT_CONFERENCE_RESULT");
		map.put(EVENT_SEPARATE_RESULT, "EVENT_SEPARATE_RESULT");
		map.put(EVENT_ECT_RESULT, "EVENT_ECT_RESULT");
		map.put(EVENT_EXIT_ECM_RESPONSE_CDMA, "EVENT_EXIT_ECM_RESPONSE_CDMA");
		map.put(EVENT_CALL_WAITING_INFO_CDMA, "EVENT_CALL_WAITING_INFO_CDMA");
		map.put(EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA, "EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA");
		EVENTS = Collections.unmodifiableMap(map);
		}

	private static void convert(Map<Method, Method> map, Method method)
		throws NoSuchMethodException {

		map.put(method, PhoneControlProxy.class.getMethod(method.getName(), method.getParameterTypes()));
		}

	private final CommandsInterface commands;
	private final Set<CallControlProxy> calls = new HashSet<CallControlProxy>();
	private Handler callStateChangedHandler;
	private Mapping[] mappings = new Mapping[9];

	public PhoneControlProxy(CommandsInterface commands) {
		this.commands = commands;
		if (ServiceManager.getService("phone-control") == null) {
			ServiceManager.addService("phone-control", this);
			}
		}

	CommandsInterface proxy() {
		return (CommandsInterface)Proxy.newProxyInstance(PhoneControlProxy.class.getClassLoader(), new Class[] { CommandsInterface.class }, this);
		}

	public ICallControl newIncomingCall(String number, ICallListener listener) {
		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i] != null) {
				if (mappings[i].state == DriverCall.State.INCOMING) {
					return null;
					}
				}
			}
		CallControlProxy call = new CallControlProxy(number, listener);
		call.state = DriverCall.State.INCOMING;
		calls.add(call);
		Message result = Message.obtain();
		result.what = EVENT_CALL_STATE_CHANGE;
		AsyncResult.forMessage(result);
		callStateChangedHandler.sendMessage(result);
		return call;
		}

	public ICallControl newOutgoingCall(String number, ICallListener listener) {
		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i] != null) {
				if (mappings[i].state == DriverCall.State.ALERTING) {
					return null;
					}
				}
			}
		CallControlProxy call = new CallControlProxy(number, listener);
		call.state = DriverCall.State.ALERTING;
		calls.add(call);
		Message result = Message.obtain();
		result.what = EVENT_CALL_STATE_CHANGE;
		AsyncResult.forMessage(result);
		callStateChangedHandler.sendMessage(result);
		return call;
		}

	public Object invoke(Object proxy, Method method, Object[] args)
		throws Throwable {

		Method m = OVERRIDES.get(method);
		if (m != null) {
			return m.invoke(this, args);
			}
		Object invoke = method.invoke(commands, args);
		return invoke;
		}

    public void registerForCallStateChanged(final Handler h, int what, Object obj) {
		callStateChangedHandler = h;
		h.callback(new Handler.Callback() {
			public boolean handleMessage(Message msg) {
				switch (msg.what) {
					case EVENT_POLL_CALLS_RESULT:
						List<DriverCall> modems = (List<DriverCall>)((AsyncResult)msg.obj).result;
						if (modems == null) {
							break;
							}
						List<DriverCall> voips = new ArrayList<DriverCall>();
						for (CallControlProxy call : calls) {
							DriverCall c = new DriverCall();
							c.index = call.index;
							c.isMT = false;
							c.state = call.state;
							c.isMpty = false;
							c.number = call.number;
							c.TOA = call.number.startsWith("+") ? 145 : 129;
							c.isVoice = true;
							c.isVoicePrivacy = false;
							c.numberPresentation = 1;
							voips.add(c);
							}
						// Copy existing modem and voip calls, remove existents
						List<DriverCall> calls = new ArrayList<DriverCall>();
						for (int i = 0; i < mappings.length; i++) {
							if (mappings[i] != null) {
								List<DriverCall> temp = mappings[i].modem ? modems : voips;
								boolean remove = true;
								for (Iterator<DriverCall> iterator = temp.iterator(); iterator.hasNext(); ) {
									DriverCall call = iterator.next();
									if (call.index == mappings[i].index) {
										mappings[i].state = call.state;
										call.index = i + 1;
										calls.add(call);
										iterator.remove();
										remove = false;
										break;
										}
									}
								if (remove) {
									mappings[i] = null;
									}
								}
							}
						// Add new modem calls
						for (Iterator<DriverCall> iterator = modems.iterator(); iterator.hasNext(); ) {
							DriverCall call = iterator.next();
							boolean processed = false;
							for (int i = 0; i < mappings.length; i++) {
								if (mappings[i] == null) {
									mappings[i] = new Mapping(true, call.index);
									mappings[i].state = call.state;
									call.index = i + 1;
									calls.add(call);
									iterator.remove();
									processed = true;
									break;
									}
								}
							if (!processed) {
								// TODO something went wrong
								}
							}
						// Add new voip calls
						for (Iterator<DriverCall> iterator = voips.iterator(); iterator.hasNext(); ) {
							DriverCall call = iterator.next();
							boolean processed = false;
							for (int i = 0; i < mappings.length; i++) {
								if (mappings[i] == null) {
									mappings[i] = new Mapping(false, call.index);
									mappings[i].state = call.state;
									call.index = i + 1;
									calls.add(call);
									iterator.remove();
									processed = true;
									break;
									}
								}
							if (!processed) {
								// TODO Something went wrong
								}
							}
						Collections.sort(calls);
						((AsyncResult)msg.obj).result = calls;
						break;
					}
				return false;
				}
			});
		commands.registerForCallStateChanged(h, what, obj);
		}

    public void hangupConnection (int gsmIndex, Message result) {
		if (gsmIndex < 1 || gsmIndex >= mappings.length || mappings[gsmIndex - 1] == null) {
			return;
			}
		int index = mappings[gsmIndex - 1].index;
		if (mappings[gsmIndex - 1].modem) {
			commands.hangupConnection(index, result);
			}
		else {
			boolean processed = false;
			for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
				CallControlProxy call = iterator.next();
				if (call.index == index) {
					call.onCancelled();
					iterator.remove();
					processed = true;
					break;
					}
				}
			if (!processed) {
				// TODO Something went wrong
				}
			AsyncResult.forMessage(result);
			callStateChangedHandler.sendMessage(result);
			}
		}

	/**
	 * Release all held calls or set the busy state for the waiting call.
	 */
    public void hangupWaitingOrBackground(Message result) {
		boolean modem = false;
		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i] != null) {
				if (mappings[i].state == DriverCall.State.INCOMING) {
					if (mappings[i].modem) {
						modem = true;
						}
					else {
						for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
							CallControlProxy call = iterator.next();
							if (call.index == mappings[i].index) {
								call.onCancelled();
								iterator.remove();
								break;
								}
							}
						}
					}
				}
			}
		if (modem) {
			commands.hangupWaitingOrBackground(result);
			}
		else {
			AsyncResult.forMessage(result);
			callStateChangedHandler.sendMessage(result);
			}
		}

	/**
	 * Release all active calls.
	 */
    public void hangupForegroundResumeBackground(Message result) {
		boolean modem = false;
		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i] != null) {
				if (mappings[i].state == DriverCall.State.ACTIVE) {
					if (mappings[i].modem) {
						modem = true;
						}
					else {
						for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
							CallControlProxy call = iterator.next();
							if (call.index == mappings[i].index) {
								call.onCancelled();
								iterator.remove();
								break;
								}
							}
						}
					}
				}
			}
		if (modem) {
			commands.hangupForegroundResumeBackground(result);
			}
		else {
			AsyncResult.forMessage(result);
			callStateChangedHandler.sendMessage(result);
			}
		}

    public void acceptCall(Message result) {
		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i] != null) {
				if (mappings[i].state == DriverCall.State.INCOMING) {
					if (mappings[i].modem) {
						commands.acceptCall(result);
						}
					else {
						for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
							CallControlProxy call = iterator.next();
							if (call.index == mappings[i].index) {
								call.state = DriverCall.State.ACTIVE;
								call.onAccepted();
								AsyncResult.forMessage(result);
								callStateChangedHandler.sendMessage(result);
								break;
								}
							}
						}
					break;
					}
				}
			}
		}

	/**
	 * Put active calls on hold and activate the waiting or held call.
	 */
    public void switchWaitingOrHoldingAndActive(Message result) {
		boolean modem = false;
		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i] != null) {
				if (mappings[i].state == DriverCall.State.ACTIVE) {
					if (mappings[i].modem) {
						modem = true;
						}
					else {
						for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
							CallControlProxy call = iterator.next();
							if (call.index == mappings[i].index) {
								call.state = DriverCall.State.HOLDING;
								call.onHeld();
								break;
								}
							}
						}
					}
				else if (mappings[i].state == DriverCall.State.HOLDING) {
					if (mappings[i].modem) {
						modem = true;
						}
					else {
						for (Iterator<CallControlProxy> iterator = calls.iterator(); iterator.hasNext(); ) {
							CallControlProxy call = iterator.next();
							if (call.index == mappings[i].index) {
								call.state = DriverCall.State.ACTIVE;
								call.onHeld();
								break;
								}
							}
						}
					}
				}
			}
		if (modem) {
			commands.switchWaitingOrHoldingAndActive(result);
			}
		else {
			AsyncResult.forMessage(result);
			callStateChangedHandler.sendMessage(result);
			}
		}
	}

