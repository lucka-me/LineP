#!/usr/bin/env python3
# coding: utf-8

from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import TLS_FTPHandler
from pyftpdlib.servers import FTPServer

'''
FTPS Server on pyftpdlib
  Reference: http://pyftpdlib.readthedocs.io/en/latest/tutorial.html#ftps-ftp-over-tls-ssl-server
'''

def main():
    authorizer = DummyAuthorizer()
    authorizer.add_user("U2018", "Tester", ".", perm="elrw")
    handler = TLS_FTPHandler
    handler.certfile = "server.crt"
    handler.keyfile = "server.key"
    handler.authorizer = authorizer
    handler.banner = "FTP Server for LineP"
    handler.tls_control_required = True
    handler.tls_data_required = True
    address = ("0.0.0.0", 2121)
    server = FTPServer(address, handler)
    print("Run the server")
    server.serve_forever()

if __name__ == '__main__':
    main()
