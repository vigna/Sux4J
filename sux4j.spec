%define section free

Name:           sux4j
Version:        3.0
Release:        1
Epoch:          0
Summary:        Succinct data structures for Java
License:        LGPLv3
Source0:        http://sux.dsi.unimi.it/%{name}-%{version}-src.tar.gz
URL:            http://sux.dsi.unimi.it/
Group:          Development/Libraries/Java
BuildArch:      noarch
Requires:       fastutil >= 6.4, java >= 1.6.0, jsap >= 2.0, dsiutils >= 2.0, junit4
Requires:       log4j
Requires:       jakarta-commons-lang
Requires:       jakarta-commons-io
Requires:       jakarta-commons-collections 
Requires:       jakarta-commons-configuration
BuildRequires:  ant, jpackage-utils >= 0:5.0, /bin/bash
BuildRequires:  fastutil >= 6.4, java >= 1.6.0, jsap >= 2.0, dsiutils >= 2.0, junit4
BuildRequires:  java-devel >= 1.6.0
BuildRequires:  java-javadoc fastutil-javadoc jsap-javadoc
BuildRequires:  log4j
BuildRequires:  jakarta-commons-lang
BuildRequires:  jakarta-commons-io
BuildRequires:  jakarta-commons-collections 
BuildRequires:  jakarta-commons-configuration
BuildRequires:  jakarta-commons-lang-javadoc
BuildRequires:  jakarta-commons-io-javadoc
BuildRequires:  jakarta-commons-collections-javadoc
BuildRequires:  jakarta-commons-configuration-javadoc
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-buildroot

%description
sux4j is an implementation of succinct data structure in Java. It provides
a number of related implementations covering ranking/selection over bit
arrays, compressed lists and minimal perfect hashing.

%package javadoc
Summary:        Javadoc for %{name}
Group:          Development/Documentation

%description javadoc
Javadoc for %{name}.

%prep
%setup -q

%build
export CLASSPATH=%(build-classpath fastutil dsiutils jsap junit log4j jakarta-commons-lang jakarta-commons-io jakarta-commons-collections jakarta-commons-configuration)
ant \
  -Dj2se.apiurl=%{_javadocdir}/java \
  -Dfastutil.apiurl=%{_javadocdir}/fastutil \
  -Ddsiutils.apiurl=%{_javadocdir}/dsiutils \
  -Dlog4j.apiurl=%{_javadocdir}/log4j \
  -Djsap.apiurl=%{_javadocdir}/jsap \
  -Djunit.apiurl=%{_javadocdir}/junit \
  jar javadoc

%install
rm -rf $RPM_BUILD_ROOT
# jars
install -dm 755 $RPM_BUILD_ROOT%{_javadir}
install -pm 644 %{name}-%{version}.jar $RPM_BUILD_ROOT%{_javadir}
ln -s %{name}-%{version}.jar $RPM_BUILD_ROOT%{_javadir}/%{name}.jar
# javadoc
install -dm 755 $RPM_BUILD_ROOT%{_javadocdir}/%{name}-%{version}
cp -pr docs/* $RPM_BUILD_ROOT%{_javadocdir}/%{name}-%{version}
ln -s %{name}-%{version} $RPM_BUILD_ROOT%{_javadocdir}/%{name} # ghost symlink

%clean
rm -rf $RPM_BUILD_ROOT


%post javadoc
rm -f %{_javadocdir}/%{name}
ln -s %{name}-%{version} %{_javadocdir}/%{name}
 
%files
%defattr(0644,root,root,0755)
%doc CHANGES COPYING COPYING.LESSER
%{_javadir}/*.jar

%files javadoc
%defattr(0644,root,root,0755)
%{_javadocdir}/%{name}-%{version}
%ghost %doc %{_javadocdir}/%{name}

%changelog
* Fri Nov 29 2010 Sebastiano Vigna <vigna at acm.org> 0:2.0.1-1
- Upgrade to 2.0.1
