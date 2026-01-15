package net.spookly.hyprox.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple IPv4/IPv6 CIDR matcher used to gate registry access.
 */
public final class CidrMatcher {
    private final List<CidrBlock> blocks;

    private CidrMatcher(List<CidrBlock> blocks) {
        this.blocks = blocks;
    }

    /**
     * Builds a matcher from CIDR strings.
     */
    public static CidrMatcher from(List<String> cidrs) {
        List<CidrBlock> blocks = new ArrayList<>();
        if (cidrs != null) {
            for (String cidr : cidrs) {
                if (cidr == null || cidr.trim().isEmpty()) {
                    continue;
                }
                blocks.add(CidrBlock.parse(cidr.trim()));
            }
        }
        return new CidrMatcher(blocks);
    }

    /**
     * Returns true when the address falls within any configured CIDR.
     */
    public boolean isAllowed(InetAddress address) {
        if (address == null) {
            return false;
        }
        for (CidrBlock block : blocks) {
            if (block.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private static final class CidrBlock {
        private final byte[] network;
        private final int prefix;

        private CidrBlock(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        /**
         * Parse a CIDR string into a block with prefix.
         */
        static CidrBlock parse(String cidr) {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }
            InetAddress address = toAddress(parts[0]);
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr, e);
            }
            byte[] network = address.getAddress();
            int maxPrefix = network.length * 8;
            if (prefix < 0 || prefix > maxPrefix) {
                throw new IllegalArgumentException("CIDR prefix out of range: " + cidr);
            }
            return new CidrBlock(network, prefix);
        }

        boolean matches(InetAddress address) {
            byte[] target = address.getAddress();
            if (target.length != network.length) {
                return false;
            }
            int bits = prefix;
            int index = 0;
            while (bits >= 8) {
                if (network[index] != target[index]) {
                    return false;
                }
                bits -= 8;
                index++;
            }
            if (bits == 0) {
                return true;
            }
            int mask = (-1) << (8 - bits);
            return (network[index] & mask) == (target[index] & mask);
        }

        private static InetAddress toAddress(String value) {
            try {
                return InetAddress.getByName(value);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR address: " + value, e);
            }
        }
    }
}
