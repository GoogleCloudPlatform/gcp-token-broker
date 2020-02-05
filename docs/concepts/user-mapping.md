# User mapping

User mapping is the function of mapping a third-party user (e.g. a Kerberos principal) to a Google Identity
(either a Google user or a Google service account).

When a client submits a request to the `GetAccessToken` endpoint, the broker first [verifies](authentication.md) the
third-party user's identity, then calls a user mapper to map the third-party user to a Google identity, and then calls
a [provider](providers.md) to generate an access token for that Google identity.

You can specify which user mapper the broker should use by setting the [`user-mapping.mapper`](settings.md#user-mappingmapper)
setting to the user mapper's class path.

## User mappers

Currently, the broker comes with one built-in user mapper: the Kerberos user mapper.

### Kerberos user mapper

_Class path:_ `com.google.cloud.broker.usermapping.KerberosUserMapper`

As its name implies, this user mapper maps Kerberos principals to Google identities. This backend is included in the
[broker server](broker-server.md) package.

To perform the Kerberos user mapping, you must provide some mapping rules in the [`user-mapping.rules`](settings.md#user-mappingrules)
setting with the following format:

```
user-mapping {
  rules = [
    { if: "<condition>", then: "<expression>" },
    ...
  ]
}
```

The `if` value is a condition that is expected to return a boolean (i.e. either `true` or `false`) to determine whether
the rule applies to a given Kerberos principal. The `then` value is an expression that is expected to return the
mapped Google identity name.

Both the `if` condition and `then` expressions use the [Jinja](https://www.palletsprojects.com/p/jinja/) syntax
(Read more about the built-in operators in the [official Jinja documentation](https://jinja.palletsprojects.com/en/master/templates/)).

When the user mapper receives a mapping request for a given Kerberos principal, the mapper loops through the list of
rules and applies the following logic:

- If the `if` condition of the current rule in the loop evaluates as `true`, then the rule is found to be applicable.
  The mapper therefore exits the loop and returns the result of the rule's `then` expression to the caller.
- If the `if` condition returns `false`, then then mapper moves on to the next rule in the loop.
- If the loop reaches the end of the list and none of the specified rules were found to be applicable, then the Kerberos
  name is rejected as un-mappable.

The Kerberos principal is available in both the `if` and `then` expressions as a variable named `principal`. This
variable has 3 attributes corresponding to the 3 possible parts of a Kerberos principal:

- `principal.primary`: The user or service name (e.g. `alice` or `hive`).
- `principal.instance`: (Optional) If present, the `instance` is separated from the `primary` with a slash (`/`). In the 
  case of a user principal, the `instance` is typically `null`. In the case of a service principal, the `instance`
  may be the fully qualified domain name of the host where the service is running.
- `principal.realm`: The principal's Kerberos realm.

Take the following example:

```
user-mapping {
  rules = [
    {
        if: "principal.primary.endsWith('-app') and principal.instance != null and principal.realm == 'YOUR.REALM.COM'",
        then: "principal.primary + '-serviceaccount@myproject.iam.gserviceaccount.com'"
    },
    {
        if: "principal.realm == 'MYREALM'",
        then: "principal.primary + '@my-domain.com'"
    }
  ]
}
```

The above example configuration would yield mappings like the following:

- `spark-app/example.com@YOUR.REALM.COM` maps to `spark-serviceaccount@myproject.iam.gserviceaccount.com`
- `alice@MYREALM` maps to `alice@my-domain.com`
- `bob@MYREALM` maps to `bob@my-domain.com`

Still with the above example configuration, the following example Kerberos names would be rejected as un-mappable:

- `spark-app/example.com@ANOTHER.REALM.COM` (rejected because the realm is not `YOUR.REALM.COM`)
- `spark-app@YOUR.REALM.COM` (rejected because the `instance` is `null`)
- `alice@FOO` (rejected because the realm is not `MYREALM`)

#### Important warning about short names

When using Hadoop, some services like Yarn and [proxy users](authentication.md#proxy-user-impersonation) like Hive
initially translate the impersonated Kerberos name to a POSIX username (often referred to as short name) that is local
to the cluster. This translation is typically performed on the Hadoop cluster by applying regular expression rules that
are defined in the `auth_to_local` attribute of the [`krb5.conf`](https://web.mit.edu/kerberos/krb5-latest/doc/admin/conf_files/krb5_conf.html)
configuration file. 

In some cases, when those services call the broker, they only provide the short name (e.g. `alice`) for the impersonated
Kerberos principal instead of the full name (e.g. `alice@YOUR.REALM.COM`). To allow these services to work with the
broker, you must provide a rule that accounts for usernames that don't include a realm, for example:

```
    {
        if: "principal.realm == null",
        then: "principal.primary + '@my-domain.com'"
    }
```

**Warning**: Before you configure a mapping rule for short names in the broker settings, make sure that the
`auth_to_local` rules are also correctly configured on your Hadoop cluster. In particular, since short names don't
include a realm, you must make sure that multiple Kerberos names with the same `primary` part but with different realms
cannot be translated to the same short name on the Hadoop cluster. Otherwise, the broker could potentially mistakenly
map two different Kerberos principals to the same Google identity.