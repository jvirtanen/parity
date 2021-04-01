/*
 * Copyright 2014 Parity authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.paritytrading.parity.reporter;

import com.paritytrading.nassau.MessageListener;
import com.paritytrading.nassau.util.MoldUDP64;
import com.paritytrading.nassau.util.SoupBinTCP;
import com.paritytrading.parity.net.pmr.PMRParser;
import com.paritytrading.parity.util.Instruments;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import org.jvirtanen.config.Configs;

class TradeReporter {

    public static void main(String[] args) {
        if (args.length != 1 && args.length != 2)
            usage();

        boolean tsv = false;

        if (args.length == 2) {
            if (!args[0].equals("-t"))
                usage();

            tsv = true;
        }

        try {
            main(config(args[tsv ? 1 : 0]), tsv);
        } catch (ConfigException | FileNotFoundException e) {
            error(e);
        } catch (IOException e) {
            fatal(e);
        }
    }

    private static void main(Config config, boolean tsv) throws IOException {
        Instruments instruments = Instruments.fromConfig(config, "instruments");

        MessageListener listener = new PMRParser(new TradeProcessor(tsv ?
                    new TSVFormat(instruments) : new DisplayFormat(instruments)));

        if (config.hasPath("trade-report.multicast-interface")) {
            NetworkInterface multicastInterface = Configs.getNetworkInterface(config, "trade-report.multicast-interface");
            InetAddress      multicastGroup     = Configs.getInetAddress(config, "trade-report.multicast-group");
            int              multicastPort      = Configs.getPort(config, "trade-report.multicast-port");
            InetAddress      requestAddress     = Configs.getInetAddress(config, "trade-report.request-address");
            int              requestPort        = Configs.getPort(config, "trade-report.request-port");

            MoldUDP64.receive(multicastInterface, new InetSocketAddress(multicastGroup, multicastPort),
                    new InetSocketAddress(requestAddress, requestPort), listener);
        } else {
            InetAddress address  = Configs.getInetAddress(config, "trade-report.address");
            int         port     = Configs.getPort(config, "trade-report.port");
            String      username = config.getString("trade-report.username");
            String      password = config.getString("trade-report.password");

            SoupBinTCP.receive(new InetSocketAddress(address, port), username, password, listener);
        }
    }

    private static Config config(String filename) throws FileNotFoundException {
        File file = new File(filename);
        if (!file.exists() || !file.isFile())
            throw new FileNotFoundException(filename + ": No such file");

        return ConfigFactory.parseFile(file);
    }

    private static void usage() {
        System.err.println("Usage: parity-reporter [-t] <configuration-file>");
        System.exit(2);
    }

    private static void error(Throwable throwable) {
        System.err.println("error: " + throwable.getMessage());
        System.exit(1);
    }

    private static void fatal(Throwable throwable) {
        System.err.println("fatal: " + throwable.getMessage());
        System.err.println();
        throwable.printStackTrace(System.err);
        System.err.println();
        System.exit(1);
    }

}
