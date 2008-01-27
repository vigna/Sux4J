%define section free

Name:           sux4j
Version:        0.2
Release:        1
Epoch:		0
Summary:        Succinct data structures for Java
License:        LGPL
Source0:	http://sux4j.dsi.unimi.it/%{name}-%{version}-src.tar.gz
URL:            http://sux4j.dsi.unimi.it/
Group:          Development/Libraries/Java
Distribution:   JPackage-like
BuildArch:      noarch
Requires:	fastutil5 >= 5.0.9, java >= 1.5.0, mg4j >= 0:2.0, colt >= 0:1.1.0, jsap, junit, log4j
BuildRequires:	ant, jpackage-utils >= 0:1.6, /bin/bash
BuildRequires:	java-devel >= 1.5.0, java-javadoc fastutil5-javadoc mg4j-javadoc colt-javadoc jsap-javadoc junit-javadoc log4j-javadoc
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-buildroot

%description
sux4j is an implementation of succinct data structure in Java.

%package javadoc
Summary:        Javadoc for %{name}
Group:          Development/Documentation

%description javadoc
Javadoc for %{name}.

%prep
%setup -q

%build
export CLASSPATH=%(build-classpath fastutil5 mg4j colt jsap log4j)
ant \
  -Dj2se.apiurl=%{_javadocdir}/java \
  -Dfastutil.apiurl=%{_javadocdir}/fastutil5 \
  -Dmg4j.apiurl=%{_javadocdir}/mg4j \
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
* Mon Nov 26 2007 Sebastiano Vigna <vigna at acm.org> 0.2
- First rank/select structures
* Mon Nov 26 2007 Sebastiano Vigna <vigna at acm.org> 0.1
- First release
