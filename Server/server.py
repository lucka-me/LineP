#!/usr/bin/env python3
# coding: utf-8

from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler
from pyftpdlib.servers import FTPServer

'''
FTP Server on pyftpdlib
  Reference: https://stackoverflow.com/questions/4994638/one-line-ftp-server-in-python
  Reference: http://pyftpdlib.readthedocs.io/en/latest/tutorial.html#a-base-ftp-server
  API Reference: http://pyftpdlib.readthedocs.io/en/latest/api.html
'''

def main():
    authorizer = DummyAuthorizer()
    authorizer.add_user("U2018", "Tester", ".", perm="elrw")
    handler = FTPHandler
    handler.authorizer = authorizer
    handler.banner = "FTP Server for LineP"
    address = ("0.0.0.0", 2121)
    server = FTPServer(address, handler)
    print("Run the server")
    server.serve_forever()

if __name__ == '__main__':
    main()
