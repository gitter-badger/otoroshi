import React, { Component } from 'react';
import { BrowserRouter as Router, Route, Link, Switch, withRouter } from 'react-router-dom';
import queryString from 'query-string';

import { ServicePage } from '../pages/ServicePage';
import { ServiceAnalyticsPage } from '../pages/ServiceAnalyticsPage';
import { DocumentationPage } from '../pages/DocumentationPage';
import { ServiceApiKeysPage } from '../pages/ServiceApiKeysPage';
import { ServiceHealthPage } from '../pages/ServiceHealthPage';
import { ServiceEventsPage } from '../pages/ServiceEventsPage';
import { ServiceLiveStatsPage } from '../pages/ServiceLiveStatsPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { DangerZonePage } from '../pages/DangerZonePage';
import { GroupsPage } from '../pages/GroupsPage';
import { HomePage } from '../pages/HomePage';
import { ServicesPage } from '../pages/ServicesPage';
import { SessionsPage } from '../pages/SessionsPage';
import { U2FRegisterPage } from '../pages/U2FRegisterPage';
import { CleverPage } from '../pages/CleverPage';
import { AuditPage } from '../pages/AuditPage';
import { Top10servicesPage } from '../pages/Top10servicesPage';
import { LoggersPage } from '../pages/LoggersPage';
import { AlertPage } from '../pages/AlertPage';
import { ServicesMapPage } from '../pages/ServicesMapPage';
import { PrivateAppsSessionsPage } from '../pages/PrivateAppsSessionsPage';
import { GlobalAnalyticsPage } from '../pages/GlobalAnalyticsPage';

import { TopBar } from '../components/TopBar';
import { ReloadNewVersion } from '../components/ReloadNewVersion';
import { DefaultSidebar } from '../components/DefaultSidebar';
import { DynamicSidebar } from '../components/DynamicSidebar';
import { DynamicTitle } from '../components/DynamicTitle';
import { WithEnv } from '../components/WithEnv';

import * as BackOfficeServices from '../services/BackOfficeServices';

import { createTooltip } from '../tooltips';

class BackOfficeAppContainer extends Component {
  constructor(props) {
    super(props);
    this.state = {
      groups: [],
      lines: [],
    };
  }

  addService = e => {
    if (e && e.preventDefault) e.preventDefault();
    BackOfficeServices.createNewService().then(r => {
      ServicePage.__willCreateService = r;
      this.props.history.push({
        pathname: `/lines/${r.env}/services/${r.id}`,
      });
    });
  };

  componentWillReceiveProps(nextProps) {
    if (nextProps.children !== this.props.children) {
      DynamicSidebar.setContent(null);
    }
  }

  componentDidMount() {
    BackOfficeServices.fetchLines().then(lines => {
      BackOfficeServices.findAllGroups().then(groups => {
        this.setState({ lines, groups });
      });
    });
  }

  decorate = (Component, props) => {
    const newProps = { ...props };
    const query = queryString.parse((props.location || { search: '' }).search) || {};
    newProps.location.query = query;
    newProps.params = newProps.match.params || {};
    return (
      <Component
        setTitle={t => DynamicTitle.setContent(t)}
        getTitle={() => DynamicTitle.getContent()}
        setSidebarContent={c => DynamicSidebar.setContent(c)}
        {...newProps}
      />
    );
  };

  render() {
    const classes = ['backoffice-container'];
    if (
      this.props.children &&
      this.props.children.type &&
      this.props.children.type.backOfficeClassName
    ) {
      classes.push(this.props.children.type.backOfficeClassName);
    }
    return (
      <div>
        <ReloadNewVersion />
        <WithEnv>{env => <TopBar {...this.props} changePassword={env.changePassword} />}</WithEnv>
        <div className="container-fluid">
          <div className="row">
            <div className="col-sm-2 sidebar" id="sidebar">
              <div className="sidebar-container">
                <div className="sidebar-content">
                  <ul className="nav nav-sidebar">
                    <li>
                      <h2>
                        <a
                          href="/bo/dashboard"
                          {...createTooltip(
                            'Home dashboard of Otoroshi displaying global metrics'
                          )}>
                          <i className="fa fa-tachometer" />Dashboard
                        </a>
                      </h2>
                    </li>
                  </ul>
                  <DynamicSidebar />
                  <DefaultSidebar lines={this.state.lines} addService={this.addService} />
                </div>
                <div className="logoContent">
                  <img className="logo" src="/assets/images/logo.svg" />
                </div>
              </div>
            </div>
            <div className="col-sm-10 col-sm-offset-2 main">
              <div className="row">
                <div className={classes.join(' ')}>
                  <DynamicTitle />
                  <div className="row">
                    <Switch>
                      <Route exact path="/" component={props => this.decorate(HomePage, props)} />
                      <Route
                        path="/lines/:lineId/services/:serviceId/stats"
                        component={props => this.decorate(ServiceLiveStatsPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/events"
                        component={props => this.decorate(ServiceEventsPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/analytics"
                        component={props => this.decorate(ServiceAnalyticsPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/health"
                        component={props => this.decorate(ServiceHealthPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/doc"
                        component={props => this.decorate(DocumentationPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/apikeys/:taction/:titem"
                        component={props => this.decorate(ServiceApiKeysPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/apikeys/:taction"
                        component={props => this.decorate(ServiceApiKeysPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId/apikeys"
                        component={props => this.decorate(ServiceApiKeysPage, props)}
                      />
                      <Route
                        path="/lines/:lineId/services/:serviceId"
                        component={props => this.decorate(ServicePage, props)}
                      />
                      <Route
                        path="/services/:taction"
                        component={props => this.decorate(ServicesPage, props)}
                      />
                      <Route
                        path="/services/:taction/:titem"
                        component={props => this.decorate(ServicesPage, props)}
                      />
                      <Route
                        path="/services"
                        component={props => this.decorate(ServicesPage, props)}
                      />
                      <Route
                        path="/groups/:taction/:titem"
                        component={props => this.decorate(GroupsPage, props)}
                      />
                      <Route
                        path="/groups/:taction"
                        component={props => this.decorate(GroupsPage, props)}
                      />
                      <Route path="/groups" component={props => this.decorate(GroupsPage, props)} />
                      <Route
                        path="/dangerzone"
                        component={props => this.decorate(DangerZonePage, props)}
                      />
                      <Route
                        path="/sessions/admin"
                        component={props => this.decorate(SessionsPage, props)}
                      />
                      <Route
                        path="/sessions/private"
                        component={props => this.decorate(PrivateAppsSessionsPage, props)}
                      />
                      <Route path="/clever" component={props => this.decorate(CleverPage, props)} />
                      <Route path="/audit" component={props => this.decorate(AuditPage, props)} />
                      <Route path="/alerts" component={props => this.decorate(AlertPage, props)} />
                      <Route
                        path="/loggers"
                        component={props => this.decorate(LoggersPage, props)}
                      />
                      <Route
                        path="/top10"
                        component={props => this.decorate(Top10servicesPage, props)}
                      />
                      <Route
                        path="/map"
                        component={props => this.decorate(ServicesMapPage, props)}
                      />
                      <Route
                        path="/stats"
                        component={props => this.decorate(GlobalAnalyticsPage, props)}
                      />
                      <Route
                        path="/admins"
                        component={props =>
                          this.decorate(U2FRegisterPage, {
                            ...props,
                          })
                        }
                      />
                      <Route component={props => this.decorate(NotFoundPage, props)} />
                    </Switch>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

const BackOfficeAppContainerWithRouter = withRouter(BackOfficeAppContainer);

export class BackOfficeApp extends Component {
  render() {
    return (
      <Router basename="/bo/dashboard">
        <BackOfficeAppContainerWithRouter />
      </Router>
    );
  }
}
