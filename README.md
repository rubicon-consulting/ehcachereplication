# Ehcache monitoring

Ehcache monitoring extends the hac in SAP Commerce Cloud to show the usage and content of the Ehcache's used in SAP Commerce Cloud.
There are a couple of Ehcache's attached the various OCC controller and the developer has no capabilities to flush the cache locally 
nor it is possible at runtime to review the cache hit/ratio to be able to tune the cache usage.

The new tab in hac will show you all Ehcache's and also allows to fetch the content of the cache and search in it. Additionally, the
new tab will also allow to flush the each individual cache or all cache on a node or on all nodes in the cluster.

## Installation

Add the extension to your SAP Commerce Cloud bin/custom folder and add the extension to you localextensions.xml. After that just 
rebuild and start SAP Commerce. Now you will see a new tab in the hac menu.

Add to your localextensions.xml
```bash
    <extension name='ehcachehac' />
```

## Usage

Login to hac and open the new tab "Ehcache --> Cache". Search for your interested Ehcache, the screen shows the cache name and the cache
manager.

## Contributing

Pull requests are welcome. For major changes, please open an issue first
to discuss what you would like to change.

## License

Register the usage at http://www.rubicon-consulting.us/register-an-sap-commerce-cloud-extension-usage/

/*
 * Copyright (c) 2023 Rubicon Consulting LLS. All rights reserved.
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions requires the registration of the usage under
 * 	  http://www.rubicon-consulting.us/register-an-sap-commerce-cloud-extension-usage/
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of Google Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */