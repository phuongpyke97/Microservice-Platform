<nav class="navbar navbar-expand-sm navbar-dark bg-dark">
  <a class="navbar-brand" href="#"><img src="eureka-logo.png" height="30" alt="" loading="lazy"> <strong>Eureka</strong></a>

  <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbar">
    <span class="navbar-toggler-icon"></span>
  </button>

  <div id="navbar" class="navbar-collapse collapse">
    <ul class="navbar-nav">
      <li class="nav-item active">
        <a class="nav-link" href="/">Home</a>
      </li>
      <#list replicas as replica>
        <#if replica.key??>
          <li class="nav-item">
            <a class="nav-link" href="${replica.key}">${(replica.value)!replica.key}</a>
          </li>
        </#if>
      </#list>
    </ul>
  </div>
</nav>
