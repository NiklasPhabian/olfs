# In test URL below, parameters are encoded to be allowed by Tomcat instances fixed for CVE-2016-6816.
# url = http://localhost:8080/opendap/data/ff/1998-6-avhrr.dat.das?&GSO_AVHRR.day_num>160&GSO_AVHRR.day_num<162
url = http://localhost:8080/opendap/data/ff/1998-6-avhrr.dat.das?%26GSO_AVHRR.day_num%3E160%26GSO_AVHRR.day_num%3C162
-s
