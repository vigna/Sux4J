%define section free

Name:           sux4j
Version:        0.3
Release:        1jpp
Epoch:		0
Summary:        Succinct data structures for Java
License:        LGPL
Source0:	http://sux.dsi.unimi.it/%{name}-%{version}-src.tar.gz
URL:            http://sux.dsi.unimi.it/
Group:          Development/Libraries/Java
Distribution:   JPackage
Vendor:		JPackage Project
BuildArch:      noarch
Requires:	fastutil5 >= 5.1.3, java >= 1.5.0, dsiutils >= 1.0.2, colt >= 0:1.1.0, jsap, junit, log4j
BuildRequires:	ant, jpackage-utils >= 0:1.6, /bin/bash
BuildRequires:	java-devel >= 1.5.0, java-javadoc fastutil5-javadoc dsiutils-javadoc colt-javadoc jsap-javadoc junit-javadoc log4j-javadoc
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-buildroot

%description
sux4j is an implementation of succinct data structure in Java. It provides
basic rank/select implementations, plus sophisticated data structures such
as minimal perfect hashes.

%package javadoc
Summary:        Javadoc for %{name}
Group:          Development/Documentation

%description javadoc
Javadoc for %{name}.

%prep
%setup -q

%build
export CLASSPATH=%(build-classpath fastutil5 dsiutils colt jsap log4j)
ant \
  -Dj2se.apiurl=%{_javadocdir}/java \
  -Dfastutil.apiurl=%{_javadocdir}/fastutil5 \
  -Ddsiutils.apiurl=%{_javadocdir}/dsiutils \
  -Dcolt.apiurl=%{_javadocdir}/colt \
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
%doc CHANGES COPYING.LIB
%{_javadir}/*.jar

%files javadoc
%defattr(0644,root,root,0755)
%{_javadocdir}/%{name}-%{version}
%ghost %doc %{_javadocdir}/%{name}

%changelog
* Sun Apr 6 2008 Sebastiano Vigna <vigna at acm.org> 0:1.0-1jpp
- Upgrade to 1.0
* Mon Nov 26 2007 Sebastiano Vigna <vigna at acm.org> 0:0.3-1jpp
- Moved bit vector classes to the DSI utilities
* Mon Nov 26 2007 Sebastiano Vigna <vigna at acm.org> 0:0.2
- First rank/select structures
* Mon Nov 26 2007 Sebastiano Vigna <vigna at acm.org> 0:0.1
- First release
