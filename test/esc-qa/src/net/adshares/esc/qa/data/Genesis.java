package net.adshares.esc.qa.data;

import java.util.List;

/**
 * genesis.json schema
 */
public class Genesis {

    private List<Node> nodes;

    public List<Node> getNodes() {
        return nodes;
    }

    public class Node {

        private String public_key;
        private String _secret;
        private String _sign;
        private List<Account> accounts;

        public String getPublicKey() {
            return public_key;
        }

        public String getSecret() {
            return _secret;
        }

        public String getSign() {
            return _sign;
        }

        public List<Account> getAccounts() {
            return accounts;
        }

        public class Account {
            private String _address;
            private String balance;
            private String public_key;
            private String _secret;
            private String _sign;

            public String getAddress() {
                return _address;
            }

            public String getBalance() {
                return balance;
            }

            public String getPublicKey() {
                return public_key;
            }

            public String getSecret() {
                return _secret;
            }

            public String getSign() {
                return _sign;
            }


        }
    }
}
