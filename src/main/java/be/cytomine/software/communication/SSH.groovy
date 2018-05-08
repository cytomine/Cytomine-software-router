package be.cytomine.software.communication


/*
 * Copyright (c) 2009-2018. Authors: see NOTICE file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import groovy.util.logging.Log4j

@Log4j
class SSH implements Communication {

    String user
    String host
    String keyFilePath
    String keyPassword = null
    int port = 22

    private Session createSession() {
        try {
            JSch jSch = new JSch()

            if (keyFilePath != null) {
                if (keyPassword != null) {
                    jSch.addIdentity(keyFilePath as String, keyPassword as String)
                } else {
                    jSch.addIdentity(keyFilePath as String)
                }
            }

            Properties properties = new Properties()
            properties.put("StrictHostKeyChecking", "no")

            Session session = jSch.getSession(user, host, port)
            session.setConfig(properties)
            session.connect()

            return session
        } catch (JSchException ex) {
            log.info(ex.toString())
            return null
        }
    }

    @Override
    def executeCommand(String command) throws JSchException, UnknownHostException {
        Session session = createSession()

        Channel channel = session.openChannel("exec")
        ((ChannelExec) channel).setCommand(command)
        channel.setInputStream(null)
        ((ChannelExec) channel).setErrStream(System.err)

        InputStream inputStream = channel.getInputStream()
        channel.connect()

        boolean closed = false
        byte[] temp = new byte[1024]
        String result = ""

        while (!closed) {
            while (inputStream.available() > 0) {
                int bytesRead = inputStream.read(temp, 0, 1024)
                if (bytesRead < 0) break
                String current = new String(temp, 0, bytesRead)
                result += current
                log.info(current)
            }
            if (channel.isClosed()) {
                log.info("exit-status: ${channel.getExitStatus()}")
                closed = true
            }
        }

        channel.disconnect()
        session.disconnect()

        return result
    }

    @Override
    def copyRemoteToLocal(def from, def to, def filename) throws JSchException, IOException, UnknownHostException {
        Session session = createSession()

        from += File.separator + filename
        def prefix = null

        if (new File(to as String).isDirectory()) {
            prefix = to + File.separator
        }

        def command = "scp -f " + from
        Channel channel = session.openChannel("exec")
        ((ChannelExec) channel).setCommand(command)

        def output = channel.getOutputStream()
        def input = channel.getInputStream()

        channel.connect()

        byte[] buf = new byte[1024]

        buf[0] = 0
        output.write(buf, 0, 1)
        output.flush()

        while (true) {
            def c = checkAck(input)
            if (c != 'C') break

            // read '0644 '
            input.read(buf, 0, 5)

            def filesize = 0L
            while (true) {
                if (input.read(buf, 0, 1) < 0) throw new JSchException("Error during the file transfer !")
                if (buf[0] == (Byte) (char) ' ') break
                filesize = filesize * 10L + (long) (buf[0] - (char) '0')
            }

            def file = null
            for (int i = 0; ; i++) {
                input.read(buf, i, 1)
                if (buf[i] == (Byte) 0x0a) {
                    file = new String(buf, 0, i)
                    break
                }
            }

            log.info("file-size = ${filesize}, file = ${file}")

            // send '\0'
            buf[0] = 0
            output.write(buf, 0, 1)
            output.flush()

            // read a content of lfile
            FileOutputStream fos = new FileOutputStream(prefix == null ? to as String : prefix + file)
            def foo
            while (true) {
                if (buf.length < filesize) foo = buf.length
                else foo = (int) filesize
                foo = input.read(buf, 0, foo)
                if (foo < 0) throw new JSchException("Error during the file transfer !")

                fos.write(buf, 0, foo)
                filesize -= foo
                if (filesize == 0L) break
            }

            if (checkAck(input) != 0) throw new JSchException("Error during the file transfer !")

            // send '\0'
            buf[0] = 0
            output.write(buf, 0, 1)
            output.flush()

            try {
                if (fos != null) fos.close()
            } catch (Exception ex) {
                System.out.println(ex)
            }
        }

        channel.disconnect()
        session.disconnect()
    }

    @Override
    def copyLocalToRemote(def from, def to, def filename) throws JSchException, IOException, UnknownHostException {
        Session session = createSession()

        def ptimestamp = true
        from = from + File.separator + filename

        // exec 'scp -t rfile' remotely
        String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + to as String
        Channel channel = session.openChannel("exec")
        ((ChannelExec) channel).setCommand(command)

        // get I/O streams for remote scp
        OutputStream output = channel.getOutputStream()
        InputStream input = channel.getInputStream()

        channel.connect()

        if (checkAck(input) != 0) throw new JSchException("Error during the file transfer !")

        File _lfile = new File(from as String)

        if (ptimestamp) {
            command = "T" + (_lfile.lastModified() / 1000) + " 0"
            // The access time should be sent here,
            // but it is not accessible with JavaAPI ;-<
            command += (" " + (_lfile.lastModified() / 1000) + " 0\n")
            output.write(command.getBytes())
            output.flush()
            if (checkAck(input) != 0) throw new JSchException("Error during the file transfer !")
        }

        // send "C0644 filesize filename", where filename should not include '/'
        long filesize = _lfile.length()
        command = "C0644 " + filesize + " "
        if (from.lastIndexOf('/') > 0) {
            command += from.substring(from.lastIndexOf('/') + 1)
        } else {
            command += from
        }

        command += "\n"
        output.write(command.getBytes())
        output.flush()

        if (checkAck(input) != 0) throw new JSchException("Error during the file transfer !")

        // send a content of lfile
        FileInputStream fis = new FileInputStream(from as String)
        byte[] buf = new byte[1024]
        while (true) {
            int len = fis.read(buf, 0, buf.length)
            if (len <= 0) break
            output.write(buf, 0, len) //out.flush()
        }

        // send '\0'
        buf[0] = 0
        output.write(buf, 0, 1)
        output.flush()

        if (checkAck(input) != 0) throw new JSchException("Error during the file transfer !")

        output.close()

        try {
            if (fis != null) fis.close()
        } catch (Exception ex) {
            System.out.println(ex)
        }

        channel.disconnect()
        session.disconnect()
    }

    private def checkAck(InputStream inputStream) {
        Integer b = inputStream.read()
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) return b
        if (b == -1) return b

        if (b == 1 || b == 2) {
            def sb = new StringBuffer()
            def c = inputStream.read()
            sb.append((char) c)
            while (c != '\n') {
                c = inputStream.read()
                sb.append((char) c)
            }
            if (b as Integer == 1) {
                println(sb.toString())
            }
            if (b as Integer == 2) {
                println(sb.toString())
            }
        }
        return b
    }

}
