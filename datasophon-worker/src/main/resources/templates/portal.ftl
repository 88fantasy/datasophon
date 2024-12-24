<#noparse>
server:
  port: 10102

  
feign:
  client:
    config:
      default:
        connectTimeout: 60000
        readTimeout: 10000

#设置feign客户端超时时间(OpenFeign默认支持ribbon)
ribbon:
 #指的是建立连接所用的时间，适用于网络状况正常的情况下,两端连接所用的时间
  ReadTimeout: 5000
 #指的是建立连接后从服务器读取到可用资源所用的时间
  ConnectTimeout: 5000
</#noparse>


    