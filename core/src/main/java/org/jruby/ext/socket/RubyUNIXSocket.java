/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2008 Ola Bini <ola.bini@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ext.socket;

import jnr.constants.platform.Fcntl;
import jnr.constants.platform.OpenFlags;
import jnr.constants.platform.SocketLevel;
import jnr.constants.platform.SocketOption;
import jnr.ffi.LastError;
import jnr.unixsocket.UnixServerSocket;
import jnr.unixsocket.UnixServerSocketChannel;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.io.ModeFlags;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channel;


@JRubyClass(name="UNIXSocket", parent="BasicSocket")
public class RubyUNIXSocket extends RubyBasicSocket {
    private static ObjectAllocator UNIXSOCKET_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new RubyUNIXSocket(runtime, klass);
        }
    };

    static void createUNIXSocket(Ruby runtime) {
        RubyClass rb_cUNIXSocket = runtime.defineClass("UNIXSocket", runtime.getClass("BasicSocket"), UNIXSOCKET_ALLOCATOR);
        runtime.getObject().setConstant("UNIXsocket", rb_cUNIXSocket);
        
        rb_cUNIXSocket.defineAnnotatedMethods(RubyUNIXSocket.class);
    }

    public RubyUNIXSocket(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }

    @JRubyMethod(visibility = Visibility.PRIVATE)
    public IRubyObject initialize(ThreadContext context, IRubyObject path) {
        init_unixsock(context.runtime, path, false);

        return context.nil;
    }

    @JRubyMethod
    public IRubyObject path(ThreadContext context) {
        return RubyString.newEmptyString(context.runtime);
    }

    @JRubyMethod
    public IRubyObject addr(ThreadContext context) {
        Ruby runtime = context.runtime;

        return runtime.newArray(
                runtime.newString("AF_UNIX"),
                RubyString.newEmptyString(runtime));
    }

    @JRubyMethod
    public IRubyObject peeraddr(ThreadContext context) {
        Ruby runtime = context.runtime;

        return runtime.newArray(
                runtime.newString("AF_UNIX"),
                runtime.newString(openFile.getPath()));
    }

    @JRubyMethod(name = "recvfrom", required = 1, optional = 1)
    public IRubyObject recvfrom(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.runtime;

        IRubyObject _length = args[0];
        IRubyObject _flags;

        if(args.length == 2) {
            _flags = args[1];
        } else {
            _flags = runtime.getNil();
        }

        // TODO
        int flags;

        _length = args[0];

        if(_flags.isNil()) {
            flags = 0;
        } else {
            flags = RubyNumeric.fix2int(_flags);
        }

        return runtime.newArray(
                recv(context, _length),
                peeraddr(context));
    }

    @JRubyMethod(notImplemented = true)
    public IRubyObject send_io(IRubyObject path) {
        //TODO: implement, won't do this now
        return  getRuntime().getNil();
    }

    @JRubyMethod(rest = true, notImplemented = true)
    public IRubyObject recv_io(IRubyObject[] args) {
        //TODO: implement, won't do this now
        return  getRuntime().getNil();
    }

    @JRubyMethod(name = {"socketpair", "pair"}, optional = 2, meta = true)
    public static IRubyObject socketpair(ThreadContext context, IRubyObject recv, IRubyObject[] args) {
        final Ruby runtime = context.runtime;

        // TODO: type and protocol

        UnixSocketChannel[] sp;

        try {
            sp = UnixSocketChannel.pair();

            final RubyClass UNIXSocket = runtime.getClass("UNIXSocket");
            RubyUNIXSocket sock = (RubyUNIXSocket)(Helpers.invoke(context, UNIXSocket, "allocate"));
            sock.init_sock(runtime, sp[0], "");

            RubyUNIXSocket sock2 = (RubyUNIXSocket)(Helpers.invoke(context, UNIXSocket, "allocate"));
            sock2.init_sock(runtime, sp[1], "");

            return runtime.newArray(sock, sock2);

        } catch (IOException ioe) {
            throw runtime.newIOErrorFromException(ioe);
        }
    }

    @Override
    public IRubyObject setsockopt(ThreadContext context, IRubyObject _level, IRubyObject _opt, IRubyObject val) {
        SocketLevel level = levelFromArg(_level);
        SocketOption opt = optionFromArg(_opt);

        switch(level) {
            case SOL_SOCKET:
                switch(opt) {
                    case SO_KEEPALIVE: {
                        // TODO: socket options
                    }
                    break;
                    default:
                        throw context.runtime.newErrnoENOPROTOOPTError();
                }
                break;
            default:
                throw context.runtime.newErrnoENOPROTOOPTError();
        }

        return context.runtime.newFixnum(0);
    }

    protected static void rb_sys_fail(Ruby runtime, String message) {
        final int n = LastError.getLastError(jnr.ffi.Runtime.getSystemRuntime());

        RubyClass instance = runtime.getErrno(n);

        if(instance == null) {
            throw runtime.newSystemCallError(message);

        } else {
            throw runtime.newErrnoFromInt(n, message);
        }
    }

    protected void init_unixsock(Ruby runtime, IRubyObject _path, boolean server) {
        ByteList path = _path.convertToString().getByteList();
        String fpath = Helpers.decodeByteList(runtime, path);

        int maxSize = 103; // Max size from Darwin, lowest common value we know of
        if (fpath.length() > 103) {
            throw runtime.newArgumentError("too long unix socket path (max: " + maxSize + "bytes)");
        }

        try {
            if (server) {
                UnixServerSocketChannel channel = UnixServerSocketChannel.open();
                UnixServerSocket socket = channel.socket();

                // TODO: listen backlog

                socket.bind(new UnixSocketAddress(new File(fpath)));

                init_sock(runtime, channel, fpath);

            } else {
                File fpathFile = new File(fpath);

                if (!fpathFile.exists()) {
                    throw runtime.newErrnoENOENTError("unix socket");
                }
                
                UnixSocketChannel channel = UnixSocketChannel.open();

                channel.connect(new UnixSocketAddress(fpathFile));

                init_sock(runtime, channel);

            }

        } catch (IOException ioe) {
            throw runtime.newIOErrorFromException(ioe);
        }
    }

    protected void init_sock(Ruby runtime, Channel channel, String path) {
        MakeOpenFile();
        
        ModeFlags modes = newModeFlags(runtime, ModeFlags.RDWR);

        openFile.setFD(newChannelFD(runtime, channel));
        openFile.setMode(modes.getOpenFileFlags());
        openFile.setSync(true);
        openFile.setPath(path);
    }

    protected void init_sock(Ruby runtime, Channel channel) {
        init_sock(runtime, channel, null);
    }

    private UnixSocketChannel asUnixSocket() {
        return (UnixSocketChannel)getOpenFile().fd().ch;
    }

    protected final static int F_GETFL = Fcntl.F_GETFL.intValue();
    protected final static int F_SETFL = Fcntl.F_SETFL.intValue();

    protected final static int O_NONBLOCK = OpenFlags.O_NONBLOCK.intValue();
}
