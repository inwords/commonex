from .dns_error import DnsError


class ApiError(DnsError):
    def __init__(self, code):
        self.code = code
        super().__init__("Yandex API failed with status {}".format(code))
