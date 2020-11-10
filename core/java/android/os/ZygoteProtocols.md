This is an incomplete description of the protocols used to communicate with the
Zygote, and by the Zygote.

The Zygote receives a sequence of commands via a socket. Each command consists
of a sequence of '\n'-terminated lines. The first line contains the decimal
number of immediately following lines that are part of the same command.

Each subsequent line contains UTF-8 encoded characters with an argument for the
command. A complete list of arguments can be found in ZygoteArguments.parseArgs.

Most commands are requests to fork a subprocess. An example of such a command is

--runtime-args
--setuid=10102
--setgid=10102
--runtime-flags=2099200
--mount-external-default
--target-sdk-version=10000
--setgroups=50102,20102,9997
--nice-name=com.android.launcher3
--seinfo=default:privapp:targetSdkVersion=30:complete
--app-data-dir=/data/user/0/com.android.launcher3
--package-name=com.android.launcher3
--disabled-compat-changes=132649864,135634846,135772972
android.app.ActivityThread
seq=6

Commands to fork a subprocess are identified by a "--runtime-args" argument.
Empirically, this always comes first, and our native command loop optimization
relies on that.

Different commands result in different kinds of replies on the socket. Some
commands result in no replies. For the fork request, the reply consists of

1) a 32-bit integer representing the actual process id of the child, which may
be a decendant of the forked child.
2) 0 or 1 byte. It is 1 if a wrapper process is used, i.e. if the returned pid
is not that of the immediate zygote child.

In non-USAP mode, the Zygote reads the entire command into a reusable buffer,
which is then visible to, and parsed by the child. When possible, the zygote
looks at the buffer only sufficiently to identify the leading "--runtime-args".
The child acknowledges completion of the fork by sending the actual process
id of a child using a one way pipe to the zygote.
